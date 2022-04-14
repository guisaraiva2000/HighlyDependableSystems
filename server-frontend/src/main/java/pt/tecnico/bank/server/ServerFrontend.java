package pt.tecnico.bank.server;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc.*;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class ServerFrontend implements Closeable {

    private final List<ManagedChannel> channels;
    private final List<ServerServiceStub> stubs;
    private final int byzantineQuorum;

    public ServerFrontend(int nByzantineServers) {
        this.stubs = new ArrayList<>();
        this.channels = new ArrayList<>();
        this.byzantineQuorum = 2 * nByzantineServers + 1;

        for (int i = 0; i < 3 * nByzantineServers + 1; i++)
            createNewChannel(8080 + i);
    }

    private void createNewChannel(int port) {
        try {
            ManagedChannel newChannel = ManagedChannelBuilder.forAddress("localhost", port).usePlaintext().build();
            this.channels.add(newChannel);
            this.stubs.add(ServerServiceGrpc.newStub(newChannel));
        } catch (RuntimeException sre) {
            System.out.println("ERROR : RecFrontend createNewChannel : Could not create channel\n"
                    + sre.getMessage());
        }
    }

    public PingResponse ping(PingRequest request) {
        boolean res = false;
        ResponseCollector resCol = null;
        while (!res) {
            try {
                resCol = new ResponseCollector();
                CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

                for (ServerServiceStub stub : this.stubs)
                    stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                            .ping(request, new ServerObserver<>(resCol, finishLatch, stub.getChannel().authority()));

                res = true;
                finishLatch.await();
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            } catch (InterruptedException e) {
                System.out.println("Error: " + e);
            }
        }

        checkServerStatus(resCol);

        return (PingResponse) resCol.responses.get(0);
    }

    public OpenAccountResponse openAccount(OpenAccountRequest request) {
        boolean res = false;
        ResponseCollector resCol = null;
        while (!res) {
            try {
                resCol = new ResponseCollector();
                CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

                for (ServerServiceStub stub : this.stubs)
                    stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .openAccount(request, new ServerObserver<>(resCol, finishLatch, stub.getChannel().authority()));

                res = true;
                finishLatch.await();
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            } catch (InterruptedException e) {
                System.out.println("Error: " + e);
            }
        }

        checkServerStatus(resCol);

        return (OpenAccountResponse) resCol.responses.get(0);
    }

    public SendAmountResponse sendAmount(SendAmountRequest request) {
        boolean res = false;
        ResponseCollector resCol = null;
        while (!res) {
            try {
                resCol = new ResponseCollector();
                CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

                for (ServerServiceStub stub : this.stubs)
                    stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .sendAmount(request, new ServerObserver<>(resCol, finishLatch, stub.getChannel().authority()));

                res = true;
                finishLatch.await();
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            } catch (InterruptedException e) {
                System.out.println("Error: " + e);
            }
        }

        checkServerStatus(resCol);

        return (SendAmountResponse) resCol.responses.get(0);
    }

    public CheckAccountResponse checkAccount(CheckAccountRequest request) {
        boolean res = false;
        ResponseCollector resCol = null;
        while (!res) {
            try {
                resCol = new ResponseCollector();
                CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

                for (ServerServiceStub stub : this.stubs)
                    stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .checkAccount(request, new ServerObserver<>(resCol, finishLatch, stub.getChannel().authority()));

                res = true;
                finishLatch.await();
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            } catch (InterruptedException e) {
                System.out.println("Error: " + e);
            }
        }

        checkServerStatus(resCol);

        return (CheckAccountResponse) resCol.responses.get(0);
    }

    public ReceiveAmountResponse receiveAmount(ReceiveAmountRequest request) {
        boolean res = false;
        ResponseCollector resCol = null;
        while (!res) {
            try {
                resCol = new ResponseCollector();
                CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

                for (ServerServiceStub stub : this.stubs)
                    stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .receiveAmount(request, new ServerObserver<>(resCol, finishLatch, stub.getChannel().authority()));

                res = true;
                finishLatch.await();
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            } catch (InterruptedException e) {
                System.out.println("Error: " + e);
            }
        }

        checkServerStatus(resCol);

        return (ReceiveAmountResponse) resCol.responses.get(0);
    }

    public AuditResponse audit(AuditRequest request) {
        boolean res = false;
        ResponseCollector resCol = null;
        while (!res) {
            try {
                resCol = new ResponseCollector();
                CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

                for (ServerServiceStub stub : this.stubs)
                    stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                        .audit(request, new ServerObserver<>(resCol, finishLatch, stub.getChannel().authority()));

                res = true;
                finishLatch.await();
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            } catch (InterruptedException e) {
                System.out.println("Error: " + e);
            }
        }

        checkServerStatus(resCol);

        return (AuditResponse) resCol.responses.get(0);
    }

    // aux
    private void exceptionHandler(StatusRuntimeException sre) {
        if (sre.getStatus().getCode() != Status.DEADLINE_EXCEEDED.getCode())
            throw sre;
        System.out.println("Request dropped.");
        System.out.println("Resending...");
    }

    private void checkServerStatus(ResponseCollector resCol) {
        if(resCol.responses.isEmpty())  // server down
            throw new StatusRuntimeException(Status.NOT_FOUND.augmentDescription("io exception"));
    }

    @Override
    public final void close() {
        this.channels.forEach(ManagedChannel::shutdown);
    }
}
