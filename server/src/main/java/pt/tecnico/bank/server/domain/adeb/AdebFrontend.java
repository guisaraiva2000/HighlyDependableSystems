package pt.tecnico.bank.server.domain.adeb;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.grpc.Adeb.EchoRequest;
import pt.tecnico.bank.server.grpc.Adeb.ReadyRequest;
import pt.tecnico.bank.server.grpc.AdebServiceGrpc;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AdebFrontend implements Closeable {

    private final List<ManagedChannel> channels;
    private final Map<String, AdebServiceGrpc.AdebServiceStub> stubs;

    public AdebFrontend(int nByzantineServers) {
        this.stubs = new HashMap<>();
        this.channels = new ArrayList<>();

        for (int i = 0; i < 3 * nByzantineServers + 1; i++)
            createNewChannel(i);
    }


    public void echo(EchoRequest request) {

        this.stubs.keySet().forEach( sName -> {
            try {
                stubs.get(sName).withDeadlineAfter(10, TimeUnit.SECONDS)
                        .echo(request, new AdebObserver<>());
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            }
        });

    }

    public void ready(ReadyRequest request) {

        this.stubs.keySet().forEach( sName -> {
            try {
                stubs.get(sName).withDeadlineAfter(10, TimeUnit.SECONDS)
                        .ready(request, new AdebObserver<>());
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            }
        });

    }


    // aux

    private void exceptionHandler(StatusRuntimeException sre) {
        if (sre.getStatus().getCode() != Status.DEADLINE_EXCEEDED.getCode())
            throw sre;

        System.out.println("Request dropped.\nResending...");

    }

    private void createNewChannel(int index) {
        try {
            ManagedChannel newChannel = ManagedChannelBuilder.forAddress("localhost", 8080 + index).usePlaintext().build();
            this.channels.add(newChannel);
            this.stubs.put("Server" + (index + 1), AdebServiceGrpc.newStub(newChannel));
        } catch (RuntimeException sre) {
            System.out.println("ERROR : RecFrontend createNewChannel : Could not create channel\n"
                    + sre.getMessage());
        }
    }

    @Override
    public final void close() {
        //this.channels.forEach(ManagedChannel::shutdown);

        for (ManagedChannel managedChannel : this.channels) {
            managedChannel.shutdown();
            try {
                if (!managedChannel.awaitTermination(3500, TimeUnit.MILLISECONDS)) {
                    managedChannel.shutdownNow();
                }
            } catch (InterruptedException e) {
                managedChannel.shutdownNow();
            }
        }
    }
}
