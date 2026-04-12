package edu.pucmm.eict;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pucmm.eict.auth.AuthMiddleware;
import edu.pucmm.eict.auth.JwtService;
import edu.pucmm.eict.dto.CreateUserRequest;
import edu.pucmm.eict.dto.LoginRequest;
import edu.pucmm.eict.dto.LoginResponse;
import edu.pucmm.eict.dto.UpdateUserRequest;
import edu.pucmm.eict.models.Formulario;
import edu.pucmm.eict.models.Usuario;
import edu.pucmm.eict.services.FormularioService;
import edu.pucmm.eict.services.UserService;
import io.javalin.Javalin;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.staticfiles.Location;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AppFactory {
    private AppFactory() {
    }

    public static Javalin create(FormularioService formularioService, UserService userService, JwtService jwtService) {
        ObjectMapper objectMapper = new ObjectMapper();
        AuthMiddleware authMiddleware = new AuthMiddleware(jwtService);

        Javalin app = Javalin.create(config -> config.staticFiles.add(staticFiles -> {
            staticFiles.hostedPath = "/";
            staticFiles.directory = "/public";
            staticFiles.location = Location.CLASSPATH;
        }));

        app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));

        app.post("/api/login", ctx -> {
            LoginRequest login = ctx.bodyAsClass(LoginRequest.class);
            Usuario user = userService.authenticate(login.getEmail(), login.getPassword());
            if (user == null) {
                ctx.status(401).json(Map.of("error", "Credenciales invalidas"));
                return;
            }
            String token = jwtService.generateToken(user);
            ctx.json(new LoginResponse(token, user));
        });

        app.before("/api/*", ctx -> {
            if (ctx.path().equals("/api/login") || ctx.path().equals("/api/health")) {
                return;
            }
            DecodedJWT jwt = authMiddleware.handle(ctx);
            ctx.attribute("jwt", jwt);
            String role = jwt.getClaim("rol").asString();

            if (ctx.path().startsWith("/api/users") && !ctx.path().equals("/api/users/me")) {
                requireAnyRole(role, Set.of("ADMIN"));
            }

            if (ctx.path().startsWith("/api/formularios") && ctx.method().name().equals("DELETE")) {
                requireAnyRole(role, Set.of("ADMIN"));
            }
            if (ctx.path().startsWith("/api/formularios") && !ctx.method().name().equals("DELETE")) {
                requireAnyRole(role, Set.of("ADMIN", "OPERADOR"));
            }
        });

        app.get("/api/users", ctx -> ctx.json(userService.listUsers()));

        app.get("/api/users/me", ctx -> {
            DecodedJWT jwt = ctx.attribute("jwt");
            if (jwt == null) {
                ctx.status(401).json(Map.of("error", "No autenticado"));
                return;
            }
            Usuario user = userService.findById(jwt.getSubject());
            if (user == null) {
                ctx.status(404).json(Map.of("error", "Usuario no encontrado"));
                return;
            }
            ctx.json(user);
        });

        app.post("/api/users", ctx -> {
            CreateUserRequest request = ctx.bodyAsClass(CreateUserRequest.class);
            if (request.getEmail() == null || request.getEmail().isBlank()
                    || request.getPassword() == null || request.getPassword().isBlank()) {
                ctx.status(400).json(Map.of("error", "Email y password son requeridos"));
                return;
            }

            try {
                Usuario created = userService.createUser(request.getNombre(), request.getEmail(), request.getPassword(), request.getRol());
                if (created == null) {
                    ctx.status(409).json(Map.of("error", "Email ya existe"));
                    return;
                }
                ctx.status(201).json(created);
            } catch (IllegalArgumentException ex) {
                ctx.status(400).json(Map.of("error", ex.getMessage()));
            }
        });

        app.put("/api/users/{id}", ctx -> {
            String id = ctx.pathParam("id");
            UpdateUserRequest request = ctx.bodyAsClass(UpdateUserRequest.class);
            try {
                Usuario updated = userService.updateUser(id, request.getNombre(), request.getPassword(), request.getRol());
                if (updated == null) {
                    ctx.status(404).json(Map.of("error", "Usuario no encontrado"));
                    return;
                }
                ctx.json(updated);
            } catch (IllegalArgumentException ex) {
                ctx.status(400).json(Map.of("error", ex.getMessage()));
            }
        });

        app.delete("/api/users/{id}", ctx -> {
            boolean deleted = userService.deleteUser(ctx.pathParam("id"));
            if (!deleted) {
                ctx.status(404).json(Map.of("error", "Usuario no encontrado"));
                return;
            }
            ctx.status(204);
        });

        app.get("/api/formularios", ctx -> ctx.json(formularioService.listAll()));

        app.post("/api/formularios", ctx -> {
            Formulario formulario = ctx.bodyAsClass(Formulario.class);
            formulario.setSincronizado(true);
            ctx.status(201).json(formularioService.create(formulario));
        });

        app.put("/api/formularios/{id}", ctx -> {
            String id = ctx.pathParam("id");
            Formulario updated = ctx.bodyAsClass(Formulario.class);
            Formulario result = formularioService.update(id, updated);
            if (result == null) {
                ctx.status(404).json(Map.of("error", "Formulario no encontrado"));
                return;
            }
            ctx.json(result);
        });

        app.delete("/api/formularios/{id}", ctx -> {
            boolean deleted = formularioService.delete(ctx.pathParam("id"));
            if (!deleted) {
                ctx.status(404).json(Map.of("error", "Formulario no encontrado"));
                return;
            }
            ctx.status(204);
        });

        app.post("/api/formularios/sync", ctx -> {
            List<Formulario> formularios = objectMapper.readValue(ctx.body(), new TypeReference<List<Formulario>>() {
            });
            ctx.json(Map.of("sincronizados", formularioService.bulkUpsert(formularios).size()));
        });

        app.ws("/sync", ws -> {
            ws.onConnect(ctx -> {
                String token = ctx.queryParam("token");
                if (token == null) {
                    ctx.session.close();
                    return;
                }
                try {
                    DecodedJWT decodedJWT = jwtService.verify(token);
                    String role = decodedJWT.getClaim("rol").asString();
                    requireAnyRole(role, Set.of("ADMIN", "OPERADOR"));
                } catch (Exception ex) {
                    ctx.session.close();
                }
            });
            ws.onMessage(ctx -> {
                List<Formulario> formularios = objectMapper.readValue(ctx.message(), new TypeReference<List<Formulario>>() {
                });
                int synced = formularioService.bulkUpsert(formularios).size();
                ctx.send("{\"status\":\"ok\",\"sincronizados\":" + synced + "}");
            });
        });

        return app;
    }

    private static void requireAnyRole(String role, Set<String> allowedRoles) {
        if (role == null || !allowedRoles.contains(role)) {
            throw new ForbiddenResponse("No tienes permisos para este recurso");
        }
    }
}

