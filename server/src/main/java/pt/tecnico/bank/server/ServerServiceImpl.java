package pt.tecnico.bank.server;

import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.server.grpc.Server.PingRequest;
import pt.tecnico.bank.server.grpc.Server.PingResponse;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;

import static io.grpc.Status.INVALID_ARGUMENT;


public class ServerServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {

    public ServerServiceImpl() {}

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

}