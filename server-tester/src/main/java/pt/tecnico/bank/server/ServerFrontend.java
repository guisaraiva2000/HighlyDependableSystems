package pt.tecnico.bank.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;

import java.io.Closeable;


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

    @Override
    public final void close() {
        channel.shutdown();
    }
}
