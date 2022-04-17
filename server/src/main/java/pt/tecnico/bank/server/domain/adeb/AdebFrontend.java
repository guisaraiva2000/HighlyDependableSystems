package pt.tecnico.bank.server.domain.adeb;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.grpc.Adeb.*;
import pt.tecnico.bank.server.grpc.AdebServiceGrpc;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class AdebFrontend implements Closeable {

    private final List<ManagedChannel> channels;
    private final Map<String, AdebServiceGrpc.AdebServiceStub> stubs;
    private final Crypto crypto;
    private final int byzantineQuorum;

    public AdebFrontend(int nByzantineServers, Crypto crypto) {
        this.stubs = new HashMap<>();
        this.channels = new ArrayList<>();
        this.byzantineQuorum = 2 * nByzantineServers + 1;
        this.crypto = crypto;

        for (int i = 0; i < 3 * nByzantineServers + 1; i++)
            createNewChannel(i);
    }


    public void echo(EchoRequest request) {

        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> echoWorker(request, finishLatch, sName));

        await(finishLatch); // waits for 2f+1 correct responses from servers
    }

    private void echoWorker(EchoRequest request, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .echo(request, new AdebObserver<>(finishLatch, sName, this.crypto, request.getNonce()));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }

    public void ready(ReadyRequest request) {

        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> readyWorker(request, finishLatch, sName));

        await(finishLatch); // waits for 2f + 1 correct responses from servers

        // todo f+1 shit
    }

    private void readyWorker(ReadyRequest request, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .ready(request, new AdebObserver<>(finishLatch, sName, this.crypto, request.getNonce()));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }

    // TODO echo
        // enviar servidores
        // esperar quorum
        // depois de 2f+1
        // devolver ao server

    // TODO ready
        // assinar input
        // enviar
        // esperar quorum
        // depois de 2f
        // devolver ao server

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


    public static void await(CountDownLatch finishLatch) {
        try {
            finishLatch.await();
        } catch (InterruptedException ignored) {
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
