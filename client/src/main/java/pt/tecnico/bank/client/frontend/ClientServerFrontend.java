package pt.tecnico.bank.client.frontend;

import io.grpc.*;
import io.grpc.protobuf.ProtoUtils;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc.*;

import java.io.Closeable;
import java.security.Key;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class ClientServerFrontend implements Closeable {

    private final List<ManagedChannel> channels;
    private final Map<String, ServerServiceStub> stubs;
    private final Crypto crypto;
    private final int byzantineQuorum;

    public ClientServerFrontend(int nByzantineServers, Crypto crypto) {
        this.stubs = new HashMap<>();
        this.channels = new ArrayList<>();
        this.byzantineQuorum = 2 * nByzantineServers + 1;
        this.crypto = crypto;

        for (int i = 0; i < 3 * nByzantineServers + 1; i++)
            createNewChannel(i);
    }


    public PingResponse ping(PingRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        for (ServerServiceStub stub : this.stubs.values())
            pingWorker(request, resCol, exceptions, finishLatch, stub);

        await(finishLatch); // blocked until BQ

        checkServerStatus(resCol, exceptions);

        return null;
    }

    private void pingWorker(PingRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, ServerServiceStub stub) {
        try {
            stub.withDeadlineAfter(2, TimeUnit.SECONDS)
                    .ping(request, new ClientObserver<>(resCol, exceptions, finishLatch, stub.getChannel().authority()));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }


    public OpenAccountResponse openAccount(OpenAccountRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> openAccountWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (!exceptions.responses.isEmpty()) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, -1)));
        }

        return getOpenAccountResponse(resCol);
    }

    private void openAccountWorker(OpenAccountRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .openAccount(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }

    private OpenAccountResponse getOpenAccountResponse(ResponseCollector resCol) {
        List<OpenAccountResponse> openAccountResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            OpenAccountResponse res = (OpenAccountResponse) resCol.responses.get(sName);

            Key pubKey = crypto.bytesToKey(res.getPublicKey());
            byte[] newSignature = crypto.getSignature(res.getSignature());

            String message = pubKey.toString();

            if (crypto.validateMessage(crypto.getPublicKey(sName), message, newSignature)) {
                openAccountResponses.add(res);
            } else {
                resCol.responses.remove(sName);
            }
        });

        return openAccountResponses.get(0);
    }


    public SendAmountResponse sendAmount(SendAmountRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> sendAmountWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (!exceptions.responses.isEmpty()) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));
        }

        return getSendAmountResponse(request.getNonce(), resCol);

    }

    private SendAmountResponse getSendAmountResponse(long nonce, ResponseCollector resCol) {
        List<SendAmountResponse> sendAmountResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            SendAmountResponse res = (SendAmountResponse) resCol.responses.get(sName);

            Key pubKey = crypto.bytesToKey(res.getPublicKey());
            long newNonce = res.getNonce();
            byte[] newSignature = crypto.getSignature(res.getSignature());

            String newMessage = pubKey.toString() + newNonce;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature) && nonce + 1 == newNonce) {
                sendAmountResponses.add(res);
            } else {
                resCol.responses.remove(sName);
            }
        });

        return sendAmountResponses.get(0);
    }

    private void sendAmountWorker(SendAmountRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .sendAmount(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }


    public CheckAccountResponse checkAccount(CheckAccountRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> checkAccountWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (!exceptions.responses.isEmpty()) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));
        }

        return getCheckAccountResponse(request.getNonce(), resCol);
    }

    private void checkAccountWorker(CheckAccountRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .checkAccount(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }

    private CheckAccountResponse getCheckAccountResponse(long nonce, ResponseCollector resCol) {
        List<CheckAccountResponse> checkAccountResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            CheckAccountResponse res = (CheckAccountResponse) resCol.responses.get(sName);
            int balance = res.getBalance();
            int pendentAmount = res.getPendentAmount();
            String transfers = res.getPendentTransfers();
            long newNonce = res.getNonce();
            byte[] newSignature = crypto.getSignature(res.getSignature());

            String newMessage = String.valueOf(balance) + pendentAmount + transfers + newNonce;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature) && nonce + 1 == newNonce) {
                checkAccountResponses.add(res);
            } else {
                resCol.responses.remove(sName);
            }

        });

        return checkAccountResponses.get(0);
    }


    public ReceiveAmountResponse receiveAmount(ReceiveAmountRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> receiveAmountWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (!exceptions.responses.isEmpty()) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));
        }

        return getReceiveAmountResponse(request.getNonce(), resCol);
    }

    private void receiveAmountWorker(ReceiveAmountRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .receiveAmount(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }

    private ReceiveAmountResponse getReceiveAmountResponse(long nonce, ResponseCollector resCol) {
        List<ReceiveAmountResponse> receiveAmountResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            ReceiveAmountResponse res = (ReceiveAmountResponse) resCol.responses.get(sName);

            Key pubKey = crypto.bytesToKey(res.getPublicKey());
            byte[] newSignature = crypto.getSignature(res.getSignature());
            long newNonce = res.getNonce();

            String newMessage = res.getRecvAmount() + pubKey.toString() + newNonce;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature) && nonce + 1 == newNonce) {

                receiveAmountResponses.add(res);

            } else {
                resCol.responses.remove(sName);
            }
        });

        return receiveAmountResponses.get(0);
    }


    public AuditResponse audit(AuditRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> auditWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (!exceptions.responses.isEmpty()) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));
        }

        return getAuditResponse(request.getNonce(), resCol);
    }

    private void auditWorker(AuditRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        try {
            stubs.get(sName).withDeadlineAfter(2, TimeUnit.SECONDS)
                    .audit(request, new ClientObserver<>(resCol, exceptions, finishLatch,sName));
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
        }
    }

    private AuditResponse getAuditResponse(long nonce, ResponseCollector resCol) {
        List<AuditResponse> auditResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            AuditResponse res = (AuditResponse) resCol.responses.get(sName);

            String transfers = res.getTransferHistory();
            long newNonce = res.getNonce();
            byte[] newSignature = crypto.getSignature(res.getSignature());

            String newMessage = transfers + newNonce;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature) && nonce + 1 == newNonce) {
                auditResponses.add(res);
            } else {
                resCol.responses.remove(sName);
            }
        });

        return auditResponses.get(0);
    }


    // aux
    private void exceptionHandler(StatusRuntimeException sre) {
        if (sre.getStatus().getCode() == Status.DEADLINE_EXCEEDED.getCode())
            System.out.println("Request dropped.\nResending...");

        throw sre;
    }

    private String exceptionsHandler(ResponseCollector exceptions, long nonce) {
        List<String> exceptionResponses = new ArrayList<>();

        exceptions.responses.keySet().forEach(sName -> {

            Throwable throwable = (Throwable) exceptions.responses.get(sName);

            Metadata metadata = Status.trailersFromThrowable(throwable);
            if (metadata != null) {

                ErrorResponse errorResponse = metadata.get(ProtoUtils.keyForProto(ErrorResponse.getDefaultInstance()));
                if (errorResponse != null) {

                    String errorMsg = errorResponse.getErrorMsg();
                    long newNonce = errorResponse.getNonce();
                    byte[] signature = crypto.getSignature(errorResponse.getSignature());

                    String message = errorMsg + newNonce;

                    if (crypto.validateMessage(crypto.getPublicKey(sName), message, signature) && nonce + 1 == newNonce) {
                        exceptionResponses.add(errorMsg);
                    }
                }
            }
        });

        return exceptionResponses.get(0);
    }

    private void checkServerStatus(ResponseCollector resCol, ResponseCollector exceptions) {
        if(resCol.responses.isEmpty() && exceptions.responses.isEmpty())  // server down
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
