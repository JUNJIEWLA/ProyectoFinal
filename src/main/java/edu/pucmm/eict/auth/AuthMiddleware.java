package edu.pucmm.eict.auth;

import com.auth0.jwt.interfaces.DecodedJWT;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;

public class AuthMiddleware {
    private final JwtService jwtService;

    public AuthMiddleware(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public DecodedJWT handle(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new UnauthorizedResponse("Token no enviado");
        }
        String token = authHeader.substring("Bearer ".length());
        if (!jwtService.isValid(token)) {
            throw new UnauthorizedResponse("Token invalido");
        }
        return jwtService.verify(token);
    }
}

