package pt.tecnico.bank.server;

import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.server.domain.ServerBackend;
import pt.tecnico.bank.server.domain.exceptions.*;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;

import static io.grpc.Status.*;


public class ServerServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {

    private final ServerBackend serverBackend;

    public ServerServiceImpl(ServerBackend serverBackend) {
        this.serverBackend = serverBackend;
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        String input = request.getInput();

        if (input.isBlank()) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Input cannot be empty!").asRuntimeException());
            return;
        }

        String output = "OK: " + input;
        PingResponse response = PingResponse.newBuilder().setOutput(output).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void openAccount(OpenAccountRequest request, StreamObserver<OpenAccountResponse> responseObserver) {
        try {

            responseObserver.onNext(serverBackend.openAccount(request.getPublicKey(), request.getSignature()));
            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {
        try {

            responseObserver.onNext(serverBackend.sendAmount(
                    request.getSourceKey(),
                    request.getDestinationKey(),
                    request.getAmount(),
                    request.getNonce(),
                    request.getTimestamp(),
                    request.getSignature()
            ));

            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {
        try {

            responseObserver.onNext(serverBackend.checkAccount(request.getPublicKey(), request.getNonce(), request.getTimestamp()));
            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public void receiveAmount(ReceiveAmountRequest request, StreamObserver<ReceiveAmountResponse> responseObserver) {
        try {

            responseObserver.onNext(serverBackend.receiveAmount(
                    request.getPublicKey(),
                    request.getSignature(),
                    request.getNonce(),
                    request.getTimestamp())
            );
            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
        try {

            responseObserver.onNext(serverBackend.audit(request.getPublicKey(), request.getNonce(), request.getTimestamp()));
            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

}