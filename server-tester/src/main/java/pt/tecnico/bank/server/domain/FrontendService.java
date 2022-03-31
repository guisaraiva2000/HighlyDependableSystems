package pt.tecnico.bank.server.domain;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc.*;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

public class FrontendService implements Closeable {

    private final ManagedChannel channel;
    private final ServerServiceBlockingStub stub;

    public FrontendService() {
         this.channel = ManagedChannelBuilder.forAddress("localhost", 8080)
                                              .usePlaintext()
                                              .build();
        this.stub = ServerServiceGrpc.newBlockingStub(channel);
    }

    public PingResponse getPingResponse(PingRequest request) {
        return stub.ping(PingRequest.newBuilder().setInput(request.getInput()).build());
    }

    public OpenAccountResponse getOpenAccountResponse(OpenAccountRequest request) {
        return stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                .openAccount(OpenAccountRequest.newBuilder()
                        .setPublicKey(request.getPublicKey())
                        .setBalance(request.getBalance()).build());
    }

    public SendAmountResponse getSendAmountResponse(SendAmountRequest request) {
        return stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                .sendAmount(SendAmountRequest.newBuilder()
                        .setAmount(request.getAmount())
                        .setSourceKey(request.getSourceKey())
                        .setDestinationKey(request.getDestinationKey())
                        .setSignature(request.getSignature())
                        .setNonce(request.getNonce())
                        .setTimestamp(request.getTimestamp())
                        .build());
    }

    public CheckAccountResponse getCheckAccountResponse(CheckAccountRequest request) {
        return stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                .checkAccount(CheckAccountRequest.newBuilder()
                        .setPublicKey(request.getPublicKey())
                        .build());
    }

    public ReceiveAmountResponse getReceiveAmountResponse(ReceiveAmountRequest request) {
        return stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                .receiveAmount(ReceiveAmountRequest.newBuilder()
                        .setPublicKey(request.getPublicKey())
                        .setSignature(request.getSignature())
                        .setNonce(request.getNonce())
                        .setTimestamp(request.getTimestamp())
                        .build());
    }

    public AuditResponse getAuditResponse(AuditRequest request) {
        return stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                .audit(AuditRequest.newBuilder()
                        .setPublicKey(request.getPublicKey())
                        .build());
    }

    @Override
    public final void close() {
        channel.shutdown();
    }

}
