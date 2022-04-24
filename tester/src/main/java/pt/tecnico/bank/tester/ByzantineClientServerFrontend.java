package pt.tecnico.bank.tester;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc.ServerServiceBlockingStub;

import java.io.Closeable;

public class ByzantineClientServerFrontend implements Closeable {

    private final ManagedChannel channel;
    private final ServerServiceBlockingStub stub;

    public ByzantineClientServerFrontend(int port) {
        this.channel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
        this.stub = ServerServiceGrpc.newBlockingStub(channel);
    }


    public OpenAccountResponse openAccount(OpenAccountRequest request) {
        return stub.openAccount(request);
    }

    public SendAmountResponse sendAmount(SendAmountRequest request) {
        return stub.sendAmount(request);
    }

    public CheckAccountResponse checkAccount(CheckAccountRequest request) {
        return stub.checkAccount(request);
    }

    public ReceiveAmountResponse receiveAmount(ReceiveAmountRequest request) {
        return stub.receiveAmount(request);
    }

    public AuditResponse audit(AuditRequest request) {
        return stub.audit(request);
    }

    public CheckAccountWriteBackResponse checkAccountWriteBack(CheckAccountWriteBackRequest request) {
        return stub.checkAccountWriteBack(request);
    }

    public AuditWriteBackResponse auditWriteBack(AuditWriteBackRequest request) {
        return stub.auditWriteBack(request);
    }

    public ProofOfWorkResponse pow(ProofOfWorkRequest request) {
        return stub.pow(request);
    }


    @Override
    public final void close() {
        channel.shutdown();
    }
}