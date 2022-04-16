package pt.tecnico.bank.client.frontend;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc.*;

import java.io.Closeable;
import java.security.Key;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class Frontend implements Closeable {

    private final List<ManagedChannel> channels;
    private final Map<String, ServerServiceStub> stubs;
    private final Crypto crypto;
    private final int byzantineQuorum;

    public Frontend(int nByzantineServers, Crypto crypto) {
        this.stubs = new HashMap<>();
        this.channels = new ArrayList<>();
        this.byzantineQuorum = 2 * nByzantineServers + 1;
        this.crypto = crypto;

        for (int i = 0; i < 3 * nByzantineServers + 1; i++)
            createNewChannel(i);
    }


    public PingResponse ping(PingRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        for (ServerServiceStub stub : this.stubs.values())
            pingWorker(request, resCol, finishLatch, stub);

        await(finishLatch); // blocked until BQ

        checkServerStatus(resCol);

        return null;
    }

    private void pingWorker(PingRequest request, ResponseCollector resCol, CountDownLatch finishLatch, ServerServiceStub stub) {
        try {
            stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .ping(request, new Observer<>(resCol, finishLatch, stub.getChannel().authority()));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }


    public OpenAccountResponse openAccount(OpenAccountRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> openAccountWorker(request, resCol, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol);

        List<OpenAccountResponse> openAccountResponses = new ArrayList<>();

        resCol.responses.keySet().forEach( sName -> {

            OpenAccountResponse res = (OpenAccountResponse) resCol.responses.get(sName);

            boolean ack = res.getAck();
            Key pubKey = crypto.bytesToKey(res.getPublicKey());
            byte[] newSignature = getSignature(res.getSignature());

            String message = ack + pubKey.toString();

            if (crypto.validateMessage(crypto.getPublicKey(sName), message, newSignature) && ack) {
                openAccountResponses.add(res);
            } else {
                resCol.responses.remove(sName);
            }

        });

        return openAccountResponses.get(0);
    }

    private void openAccountWorker(OpenAccountRequest request, ResponseCollector resCol, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .openAccount(request, new Observer<>(resCol, finishLatch, sName));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }


    public SendAmountResponse sendAmount(SendAmountRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> sendAmountWorker(request, resCol, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol);

        List<SendAmountResponse> sendAmountResponses = new ArrayList<>();

        resCol.responses.keySet().forEach( sName -> {

            SendAmountResponse res = (SendAmountResponse) resCol.responses.get(sName);

            boolean ack = res.getAck();
            Key pubKey = crypto.bytesToKey(res.getPublicKey());
            long newNonce = res.getNonce();
            byte[] newSignature = getSignature(res.getSignature());

            String newMessage = ack + pubKey.toString() + newNonce;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature) && ack &&
                    request.getNonce() + 1 == newNonce) {

                sendAmountResponses.add(res);

            } else {
                resCol.responses.remove(sName);
            }
        });

        return sendAmountResponses.get(0);
    }

    private void sendAmountWorker(SendAmountRequest request, ResponseCollector resCol, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .sendAmount(request, new Observer<>(resCol, finishLatch, sName));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }


    public CheckAccountResponse checkAccount(CheckAccountRequest request) {

        ResponseCollector resCol =  new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> checkAccountWorker(request, resCol, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol);

        List<CheckAccountResponse> checkAccountResponses = new ArrayList<>();

        resCol.responses.keySet().forEach( sName -> {

            CheckAccountResponse res = (CheckAccountResponse) resCol.responses.get(sName);
            int balance = res.getBalance();
            int pendentAmount = res.getPendentAmount();
            String transfers = res.getPendentTransfers();
            byte[] newSignature = getSignature(res.getSignature());

            String newMessage = String.valueOf(balance) + pendentAmount + transfers;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature)) {
                checkAccountResponses.add(res);
            } else {
                resCol.responses.remove(sName);
            }

        });

        return checkAccountResponses.get(0);
    }

    private void checkAccountWorker(CheckAccountRequest request, ResponseCollector resCol, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .checkAccount(request, new Observer<>(resCol, finishLatch, sName));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }


    public ReceiveAmountResponse receiveAmount(ReceiveAmountRequest request) {

        ResponseCollector resCol =  new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> receiveAmountWorker(request, resCol, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol);

        List<ReceiveAmountResponse> receiveAmountResponses = new ArrayList<>();

        resCol.responses.keySet().forEach( sName -> {

            ReceiveAmountResponse res = (ReceiveAmountResponse) resCol.responses.get(sName);

            boolean ack = res.getAck();
            Key pubKey = crypto.bytesToKey(res.getPublicKey());
            byte[] newSignature = getSignature(res.getSignature());
            long newNonce = res.getNonce();

            String newMessage = String.valueOf(ack) + res.getRecvAmount() + pubKey.toString() + newNonce;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature) && ack &&
                    request.getNonce() + 1 == newNonce) {

                receiveAmountResponses.add(res);

            } else {
                resCol.responses.remove(sName);
            }

        });

        return receiveAmountResponses.get(0);
    }


    private void receiveAmountWorker(ReceiveAmountRequest request, ResponseCollector resCol, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .receiveAmount(request, new Observer<>(resCol, finishLatch, sName));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }


    public AuditResponse audit(AuditRequest request) {

        ResponseCollector resCol =  new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> auditWorker(request, resCol, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol);

        List<AuditResponse> auditResponses = new ArrayList<>();

        resCol.responses.keySet().forEach( sName -> {

            AuditResponse res = (AuditResponse) resCol.responses.get(sName);

            String transfers = res.getTransferHistory();
            byte[] newSignature = getSignature(res.getSignature());

            if (crypto.validateMessage(crypto.getPublicKey(sName), transfers, newSignature)) {
                auditResponses.add(res);
            } else {
                resCol.responses.remove(sName);
            }

        });

        return auditResponses.get(0);
    }

    private void auditWorker(AuditRequest request, ResponseCollector resCol, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .audit(request, new Observer<>(resCol, finishLatch,sName));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }

    // aux
    private void exceptionHandler(StatusRuntimeException sre) {
        if (sre.getStatus().getCode() == Status.DEADLINE_EXCEEDED.getCode())
            System.out.println("Request dropped.\nResending...");

        throw sre;
    }

    private void checkServerStatus(ResponseCollector resCol) {
        if(resCol.responses.isEmpty())  // server down
            throw new StatusRuntimeException(Status.NOT_FOUND.augmentDescription("io exception"));
    }

    public static void await(CountDownLatch finishLatch) {
        try {
            finishLatch.await();
        } catch (InterruptedException ignored) {
        }
    }

    private void createNewChannel(int index) {
        try {
            ManagedChannel newChannel = ManagedChannelBuilder.forAddress("localhost", 8080 + index).usePlaintext().build();
            this.channels.add(newChannel);
            this.stubs.put("Server" + (index + 1), ServerServiceGrpc.newStub(newChannel));
        } catch (RuntimeException sre) {
            System.out.println("ERROR : RecFrontend createNewChannel : Could not create channel\n"
                    + sre.getMessage());
        }
    }

    private byte[] getSignature(ByteString res) {
        byte[] newSignature = new byte[256];
        res.copyTo(newSignature, 0);
        return newSignature;
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
