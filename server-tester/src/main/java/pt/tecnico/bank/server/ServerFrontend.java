package pt.tecnico.bank.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
        this.channel = ManagedChannelBuilder.forAddress("localhost", 8080)
                                            .usePlaintext()
                                            .build();
        this.stub = ServerServiceGrpc.newBlockingStub(this.channel);
    }

    /* ---------- Services ---------- */

    public PingResponse ping(PingRequest request) {
        return stub.ping(PingRequest.newBuilder().setInput(request.getInput()).build());
    }

    public OpenAccountResponse openAccount(OpenAccountRequest request) {
        try {
            return stub.withDeadlineAfter(1, TimeUnit.SECONDS).openAccount(OpenAccountRequest.newBuilder()
                    .setPublicKey(request.getPublicKey())
                    .setBalance(request.getBalance()).build());
        } catch (StatusRuntimeException sre) {
            if (sre.getStatus().getCode() != Status.DEADLINE_EXCEEDED.getCode())
                throw sre;
            System.out.println("Request dropped.");
            System.out.println("Resending...");
            return stub.withDeadlineAfter(1, TimeUnit.SECONDS).openAccount(OpenAccountRequest.newBuilder()
                    .setPublicKey(request.getPublicKey())
                    .setBalance(request.getBalance()).build());
        }
    }

    public SendAmountResponse sendAmount(SendAmountRequest request) {
        try {
            return stub.sendAmount(SendAmountRequest.newBuilder()
                    .setAmount(request.getAmount())
                    .setSourceKey(request.getSourceKey())
                    .setDestinationKey(request.getDestinationKey())
                    .setSignature(request.getSignature())
                    .setNonce(request.getNonce())
                    .setTimestamp(request.getTimestamp())
                    .build());
        } catch (StatusRuntimeException sre) {
            if (sre.getStatus().getCode() != Status.DEADLINE_EXCEEDED.getCode())
                throw sre;
            System.out.println("Request dropped.");
            System.out.println("Resending...");
            return stub.sendAmount(SendAmountRequest.newBuilder()
                    .setAmount(request.getAmount())
                    .setSourceKey(request.getSourceKey())
                    .setDestinationKey(request.getDestinationKey())
                    .setSignature(request.getSignature())
                    .setNonce(request.getNonce())
                    .setTimestamp(request.getTimestamp())
                    .build());
        }
    }

    public CheckAccountResponse checkAccount(CheckAccountRequest request) {
        try {
            return stub.checkAccount(CheckAccountRequest.newBuilder().setPublicKey(request.getPublicKey()).build());
        } catch (StatusRuntimeException sre) {
            if (sre.getStatus().getCode() != Status.DEADLINE_EXCEEDED.getCode())
                throw sre;
            System.out.println("Request dropped.");
            System.out.println("Resending...");
            return stub.checkAccount(CheckAccountRequest.newBuilder().setPublicKey(request.getPublicKey()).build());
        }
    }

    public ReceiveAmountResponse receiveAmount(ReceiveAmountRequest request) {
        try {
            return stub.receiveAmount(ReceiveAmountRequest.newBuilder()
                    .setPublicKey(request.getPublicKey())
                    .setSignature(request.getSignature())
                    .setNonce(request.getNonce())
                    .setTimestamp(request.getTimestamp())
                    .build());
        } catch (StatusRuntimeException sre) {
            if (sre.getStatus().getCode() != Status.DEADLINE_EXCEEDED.getCode())
                throw sre;
            System.out.println("Request dropped.");
            System.out.println("Resending...");
            return stub.receiveAmount(ReceiveAmountRequest.newBuilder()
                    .setPublicKey(request.getPublicKey())
                    .setSignature(request.getSignature())
                    .setNonce(request.getNonce())
                    .setTimestamp(request.getTimestamp())
                    .build());
        }
    }

    public AuditResponse auditResponse(AuditRequest request) {
        try {
            return stub.audit(AuditRequest.newBuilder().setPublicKey(request.getPublicKey()).build());
        } catch (StatusRuntimeException sre) {
            if (sre.getStatus().getCode() != Status.DEADLINE_EXCEEDED.getCode())
                throw sre;
            System.out.println("Request dropped.");
            System.out.println("Resending...");
            return stub.audit(AuditRequest.newBuilder().setPublicKey(request.getPublicKey()).build());
        }
    }

    @Override
    public final void close() {
        channel.shutdown();
    }

}
