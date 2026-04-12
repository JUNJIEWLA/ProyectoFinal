package edu.pucmm.eict.grpcserver;

import edu.pucmm.eict.services.FormularioService;
import edu.pucmm.eict.services.UserService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;

public class GrpcServer {
    private final Server server;

    public GrpcServer(int port, FormularioService formularioService, UserService userService) {
        this.server = ServerBuilder.forPort(port)
                .addService(new FormularioGrpcService(formularioService, userService))
                .build();
    }

    public void start() throws IOException {
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
}

