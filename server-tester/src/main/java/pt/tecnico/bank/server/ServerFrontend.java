package pt.tecnico.bank.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;


public class ServerFrontend implements Closeable {

    private final ManagedChannel channel;
    private final ServerServiceGrpc.ServerServiceBlockingStub stub;

    /**
     * Creates a frontend that contacts the only replica.
     */
    public ServerFrontend() {
        this.channel = ManagedChannelBuilder.forAddress("localhost", 8080).usePlaintext().build();
        this.stub = ServerServiceGrpc.newBlockingStub(this.channel);
    }

    /* ---------- Services ---------- */

    public PingResponse ping(PingRequest request) {
        return stub.ping(PingRequest.newBuilder().setInput(request.getInput()).build());
    }

    public OpenAccountResponse openAccount(OpenAccountRequest request) {
        return stub.withDeadlineAfter(2, TimeUnit.SECONDS).openAccount(OpenAccountRequest.newBuilder()
                .setPublicKey(request.getPublicKey())
                .setBalance(request.getBalance()).build());
    }

    public SendAmountResponse sendAmount(SendAmountRequest request) {
        return stub.withDeadlineAfter(2, TimeUnit.SECONDS).sendAmount(SendAmountRequest.newBuilder()
                .setAmount(request.getAmount())
                .setSourceKey(request.getSourceKey())
                .setDestinationKey(request.getDestinationKey())
                .setSignature(request.getSignature())
                .setNonce(request.getNonce())
                .setTimestamp(request.getTimestamp())
                .build());
    }

    public CheckAccountResponse checkAccount(CheckAccountRequest request) {
        return stub.checkAccount(CheckAccountRequest.newBuilder().setPublicKey(request.getPublicKey()).build());
    }

    public ReceiveAmountResponse receiveAmount(ReceiveAmountRequest request) {
        return stub.receiveAmount(ReceiveAmountRequest.newBuilder()
                .setPublicKey(request.getPublicKey())
                .setSignature(request.getSignature())
                .setNonce(request.getNonce())
                .setTimestamp(request.getTimestamp())
                .build());
    }

    public AuditResponse auditResponse(AuditRequest request) {
        return stub.audit(AuditRequest.newBuilder().setPublicKey(request.getPublicKey()).build());
    }

    @Override
    public final void close() {
        channel.shutdown();
    }
}
