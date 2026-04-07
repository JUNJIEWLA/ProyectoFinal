package edu.pucmm.eict;

import edu.pucmm.eict.auth.JwtService;
import edu.pucmm.eict.grpcserver.GrpcServer;
import edu.pucmm.eict.services.FormularioService;
import edu.pucmm.eict.services.UserService;
import io.javalin.Javalin;

public class Main {
    public static void main(String[] args) {
        JwtService jwtService = new JwtService();
        UserService userService = new UserService();
        FormularioService formularioService = new FormularioService();

        int httpPort = Integer.parseInt(System.getenv().getOrDefault("PORT", "7000"));
        int grpcPort = Integer.parseInt(System.getenv().getOrDefault("GRPC_PORT", "50051"));

        Javalin app = AppFactory.create(formularioService, userService, jwtService).start(httpPort);

        GrpcServer grpcServer = new GrpcServer(grpcPort, formularioService);
        try {
            grpcServer.start();
        } catch (Exception ex) {
            app.stop();
            throw new RuntimeException("No se pudo iniciar el servidor gRPC", ex);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            grpcServer.stop();
            app.stop();
        }));
    }
}
