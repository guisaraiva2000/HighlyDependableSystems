package pt.tecnico.bank.server.domain.adeb;

import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.server.domain.ServerBackend;
import pt.tecnico.bank.server.domain.exceptions.ServerStatusRuntimeException;
import pt.tecnico.bank.server.grpc.Adeb.EchoRequest;
import pt.tecnico.bank.server.grpc.Adeb.EchoResponse;
import pt.tecnico.bank.server.grpc.Adeb.ReadyRequest;
import pt.tecnico.bank.server.grpc.Adeb.ReadyResponse;
import pt.tecnico.bank.server.grpc.AdebServiceGrpc;

import static io.grpc.Status.INTERNAL;

public class AdebServiceImpl extends AdebServiceGrpc.AdebServiceImplBase {

    private final ServerBackend serverBackend;

    public AdebServiceImpl(ServerBackend serverBackend) {
        this.serverBackend = serverBackend;
    }

    @Override
    public void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
        try {

            responseObserver.onNext(serverBackend.echo(request.getKey(), request.getSignature(), request.getNonce(), request.getInput()));
            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public void ready(ReadyRequest request, StreamObserver<ReadyResponse> responseObserver) {
        try {

            responseObserver.onNext(serverBackend.ready(request.getKey(), request.getSignature(), request.getNonce(), request.getInput()));
            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

}
