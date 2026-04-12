package edu.pucmm.eict.grpcserver;

import edu.pucmm.eict.grpc.EncuestaServiceGrpc;
import edu.pucmm.eict.grpc.FormularioDTO;
import edu.pucmm.eict.grpc.FormularioRequest;
import edu.pucmm.eict.grpc.FormularioResponse;
import edu.pucmm.eict.grpc.FormulariosResponse;
import edu.pucmm.eict.grpc.UsuarioRequest;
import edu.pucmm.eict.models.Formulario;
import edu.pucmm.eict.models.Usuario;
import edu.pucmm.eict.services.FormularioService;
import edu.pucmm.eict.services.UserService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

public class FormularioGrpcService extends EncuestaServiceGrpc.EncuestaServiceImplBase {
    private final FormularioService formularioService;
    private final UserService userService;

    public FormularioGrpcService(FormularioService formularioService, UserService userService) {
        this.formularioService = formularioService;
        this.userService = userService;
    }

    @Override
    public void listarFormularios(UsuarioRequest request, StreamObserver<FormulariosResponse> responseObserver) {
        String usuarioIdOrEmail = request.getUsuarioId();
        if (usuarioIdOrEmail == null || usuarioIdOrEmail.isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("usuario_id requerido").asRuntimeException());
            return;
        }

        String usuarioRegistro = resolveUsuarioRegistro(usuarioIdOrEmail);
        if (usuarioRegistro == null) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Usuario no encontrado").asRuntimeException());
            return;
        }

        FormulariosResponse.Builder response = FormulariosResponse.newBuilder();
        for (Formulario item : formularioService.listByUsuarioRegistro(usuarioRegistro)) {
            response.addItems(FormularioDTO.newBuilder()
                    .setId(item.getId() == null ? "" : item.getId())
                    .setNombre(item.getNombre() == null ? "" : item.getNombre())
                    .setSector(item.getSector() == null ? "" : item.getSector())
                    .setNivelEscolar(item.getNivelEscolar() == null ? "" : item.getNivelEscolar())
                    .setUsuarioRegistro(item.getUsuarioRegistro() == null ? "" : item.getUsuarioRegistro())
                    .setLatitud(item.getLatitud())
                    .setLongitud(item.getLongitud())
                    .build());
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    @Override
    public void crearFormulario(FormularioRequest request, StreamObserver<FormularioResponse> responseObserver) {
        Formulario formulario = new Formulario();
        formulario.setNombre(request.getNombre());
        formulario.setSector(request.getSector());
        formulario.setNivelEscolar(request.getNivelEscolar());
        formulario.setUsuarioRegistro(request.getUsuarioRegistro());
        formulario.setLatitud(request.getLatitud());
        formulario.setLongitud(request.getLongitud());
        formulario.setFotografia(request.getFotografia());
        formulario.setSincronizado(true);

        Formulario saved = formularioService.create(formulario);
        FormularioResponse response = FormularioResponse.newBuilder()
                .setId(saved.getId())
                .setOk(true)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private String resolveUsuarioRegistro(String usuarioIdOrEmail) {
        if (usuarioIdOrEmail == null || usuarioIdOrEmail.isBlank()) {
            return null;
        }
        String value = usuarioIdOrEmail.trim();
        if (value.contains("@")) {
            return value.toLowerCase();
        }
        Usuario user = userService.findById(value);
        return user != null ? user.getEmail() : null;
    }
}

