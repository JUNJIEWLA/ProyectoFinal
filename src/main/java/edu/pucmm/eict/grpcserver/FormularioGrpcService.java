package edu.pucmm.eict.grpcserver;

import edu.pucmm.eict.grpc.EncuestaServiceGrpc;
import edu.pucmm.eict.grpc.FormularioDTO;
import edu.pucmm.eict.grpc.FormularioRequest;
import edu.pucmm.eict.grpc.FormularioResponse;
import edu.pucmm.eict.grpc.FormulariosResponse;
import edu.pucmm.eict.grpc.UsuarioRequest;
import edu.pucmm.eict.models.Formulario;
import edu.pucmm.eict.services.FormularioService;
import io.grpc.stub.StreamObserver;

public class FormularioGrpcService extends EncuestaServiceGrpc.EncuestaServiceImplBase {
    private final FormularioService formularioService;

    public FormularioGrpcService(FormularioService formularioService) {
        this.formularioService = formularioService;
    }

    @Override
    public void listarFormularios(UsuarioRequest request, StreamObserver<FormulariosResponse> responseObserver) {
        FormulariosResponse.Builder response = FormulariosResponse.newBuilder();
        for (Formulario item : formularioService.listAll()) {
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
}

