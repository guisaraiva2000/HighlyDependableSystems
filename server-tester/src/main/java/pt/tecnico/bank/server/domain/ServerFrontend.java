package pt.tecnico.bank.server.domain;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc.*;

import java.io.Closeable;
import java.util.concurrent.TimeUnit;

public class ServerFrontend implements Closeable {

    private final ManagedChannel channel;
    private final ServerServiceBlockingStub stub;

    public ServerFrontend() {
         this.channel = ManagedChannelBuilder.forAddress("localhost", 8080)
                                              .usePlaintext()
                                              .build();
        this.stub = ServerServiceGrpc.newBlockingStub(channel);
    }

    public PingResponse getPingResponse(PingRequest request) {
        PingResponse res = null;
        while (res == null) {
            try {
                res = stub.ping(PingRequest.newBuilder().setInput(request.getInput()).build());
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            }
        }
        return res;
    }

    public OpenAccountResponse getOpenAccountResponse(OpenAccountRequest request) {
        OpenAccountResponse res = null;
        while (res == null) {
            try {
                res = stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                        .openAccount(OpenAccountRequest.newBuilder()
                                .setPublicKey(request.getPublicKey())
                                .setBalance(request.getBalance()).build());
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            }
        }
        return res;
    }

    public SendAmountResponse getSendAmountResponse(SendAmountRequest request) {
        SendAmountResponse res = null;
        while (res == null) {
            try {
                res = stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                        .sendAmount(SendAmountRequest.newBuilder()
                                .setAmount(request.getAmount())
                                .setSourceKey(request.getSourceKey())
                                .setDestinationKey(request.getDestinationKey())
                                .setSignature(request.getSignature())
                                .setNonce(request.getNonce())
                                .setTimestamp(request.getTimestamp())
                                .build());
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            }
        }
        return res;
    }

    public CheckAccountResponse getCheckAccountResponse(CheckAccountRequest request) {
        CheckAccountResponse res = null;
        while (res == null) {
            try {
                res = stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                        .checkAccount(CheckAccountRequest.newBuilder()
                                .setPublicKey(request.getPublicKey())
                                .build());
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            }
        }
        return res;
    }

    public ReceiveAmountResponse getReceiveAmountResponse(ReceiveAmountRequest request) {
        ReceiveAmountResponse res = null;
        while (res == null) {
            try {
                res = stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                        .receiveAmount(ReceiveAmountRequest.newBuilder()
                                .setPublicKey(request.getPublicKey())
                                .setSignature(request.getSignature())
                                .setNonce(request.getNonce())
                                .setTimestamp(request.getTimestamp())
                                .build());
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            }
        }
        return res;
    }

    public AuditResponse getAuditResponse(AuditRequest request) {
        AuditResponse res = null;
        while (res == null) {
            try {
                res = stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                        .audit(AuditRequest.newBuilder()
                                .setPublicKey(request.getPublicKey())
                                .build());
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            }
        }
        return res;
    }

    // aux
    private void exceptionHandler(StatusRuntimeException sre) {
        if (sre.getStatus().getCode() != Status.DEADLINE_EXCEEDED.getCode())
            throw sre;
        System.out.println("Request dropped.");
        System.out.println("Resending...");
    }

    @Override
    public final void close() {
        channel.shutdown();
    }
}
