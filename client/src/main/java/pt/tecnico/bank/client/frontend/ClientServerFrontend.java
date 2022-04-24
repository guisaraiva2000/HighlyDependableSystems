package pt.tecnico.bank.client.frontend;

import com.google.protobuf.ByteString;
import io.grpc.*;
import io.grpc.protobuf.ProtoUtils;
import pt.tecnico.bank.client.exceptions.DefaultErrorException;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc.ServerServiceStub;

import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class ClientServerFrontend implements AutoCloseable {

    private final List<ManagedChannel> channels;
    private final Map<String, ServerServiceStub> stubs;
    private final Crypto crypto;
    private final int byzantineQuorum;

    public ClientServerFrontend(int nByzantineServers, Crypto crypto) {
        this.stubs = new HashMap<>();
        this.channels = new ArrayList<>();
        this.crypto = crypto;

        int nServers = 3 * nByzantineServers + 1;
        this.byzantineQuorum = (nServers + nByzantineServers) / 2 + 1;

        for (int i = 0; i < nServers; i++)
            createNewChannel(i);
    }


    public void openAccount(OpenAccountRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> openAccountWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (exceptions.responses.size() == this.byzantineQuorum) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, -1)));
        }

        getOpenAccountResponse(resCol);
    }

    private void openAccountWorker(OpenAccountRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        while (true) {
            try {
                stubs.get(sName).withDeadlineAfter(10, TimeUnit.SECONDS)
                        .openAccount(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
                break;
            } catch (StatusRuntimeException sre) {
                hasDroppedOrThrowException(sre);
            }
        }
    }

    private void getOpenAccountResponse(ResponseCollector resCol) {
        List<OpenAccountResponse> openAccountResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            OpenAccountResponse res = (OpenAccountResponse) resCol.responses.get(sName);

            PublicKey pubKey = crypto.bytesToKey(res.getPublicKey());
            byte[] newSignature = crypto.byteStringToByteArray(res.getSignature());

            String username = res.getUsername();

            String message = username + pubKey.toString();

            if (crypto.validateMessage(crypto.getPublicKey(sName), message, newSignature))
                openAccountResponses.add(res);
        });

        if (openAccountResponses.isEmpty())
            throw new DefaultErrorException();

    }


    public void sendAmount(SendAmountRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> sendAmountWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (exceptions.responses.size() == this.byzantineQuorum) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));
        }

        getSendAmountResponse(request.getNonce(), request.getTransaction().getWid(), resCol);

    }

    private void sendAmountWorker(SendAmountRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        while (true) {
            try {
                stubs.get(sName).withDeadlineAfter(10, TimeUnit.SECONDS)
                        .sendAmount(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
                break;
            } catch (StatusRuntimeException sre) {
                hasDroppedOrThrowException(sre);
            }
        }
    }

    private void getSendAmountResponse(long nonce, int myWid, ResponseCollector resCol) {
        List<SendAmountResponse> sendAmountResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            SendAmountResponse res = (SendAmountResponse) resCol.responses.get(sName);

            PublicKey pubKey = crypto.bytesToKey(res.getPublicKey());
            long newNonce = res.getNonce();
            int wid = res.getWid();
            byte[] newSignature = crypto.byteStringToByteArray(res.getSignature());

            String newMessage = pubKey.toString() + newNonce + wid;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature)
                    && nonce + 1 == newNonce
                    && wid == myWid
            )
                sendAmountResponses.add(res);

        });

        if (sendAmountResponses.isEmpty())
            throw new DefaultErrorException();
    }


    public CheckAccountResponse checkAccount(CheckAccountRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> checkAccountWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (exceptions.responses.size() == this.byzantineQuorum) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));
        }

        return getCheckAccountResponse(request.getNonce(), request.getRid(), request.getCheckKey(), resCol);
    }

    private void checkAccountWorker(CheckAccountRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        while (true) {
            try {
                stubs.get(sName).withDeadlineAfter(10, TimeUnit.SECONDS)
                        .checkAccount(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
                break;
            } catch (StatusRuntimeException sre) {
                hasDroppedOrThrowException(sre);
            }
        }
    }

    private CheckAccountResponse getCheckAccountResponse(long nonce, int myRid, ByteString checkKey, ResponseCollector resCol) {
        List<CheckAccountResponse> checkAccountResponses = new ArrayList<>();

        PublicKey chKey = crypto.bytesToKey(checkKey);

        resCol.responses.keySet().forEach(sName -> {

            CheckAccountResponse res = (CheckAccountResponse) resCol.responses.get(sName);

            List<Transaction> pendingTransactions = res.getPendingTransactionsList();
            long newNonce = res.getNonce();
            List<AdebProof> adebProofs = res.getAdebProofsList();
            int rid = res.getRid();
            int balance = res.getBalance();
            int wid = res.getWid();
            byte[] pairSig = crypto.byteStringToByteArray(res.getPairSignature());
            byte[] newSignature = crypto.byteStringToByteArray(res.getSignature());

            String newMessage = pendingTransactions.toString() + newNonce + adebProofs + rid + balance + wid + Arrays.toString(pairSig);

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature)
                    && crypto.validateMessage(chKey, String.valueOf(wid) + balance, pairSig)
                    && nonce + 1 == newNonce
                    && myRid == rid
                    && validateAdebProof(adebProofs, wid)
                    && validateTransactions(pendingTransactions, true)
                    && !hasDuplicatedTransactions(pendingTransactions)
            )
                checkAccountResponses.add(res);

        });

        if (checkAccountResponses.isEmpty())
            throw new DefaultErrorException();

        return Collections.max(checkAccountResponses, Comparator.comparing(CheckAccountResponse::getWid));
    }


    public void receiveAmount(ReceiveAmountRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> receiveAmountWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (exceptions.responses.size() == this.byzantineQuorum) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));
        }

        getReceiveAmountResponse(request.getNonce(), request.getWid(), resCol);
    }

    private void receiveAmountWorker(ReceiveAmountRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        while (true) {
            try {
                stubs.get(sName).withDeadlineAfter(10, TimeUnit.SECONDS)
                        .receiveAmount(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
                break;
            } catch (StatusRuntimeException sre) {
                hasDroppedOrThrowException(sre);
            }
        }
    }

    private void getReceiveAmountResponse(long nonce, int myWid, ResponseCollector resCol) {
        List<ReceiveAmountResponse> receiveAmountResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            ReceiveAmountResponse res = (ReceiveAmountResponse) resCol.responses.get(sName);

            PublicKey pubKey = crypto.bytesToKey(res.getPublicKey());
            long newNonce = res.getNonce();
            int wid = res.getWid();

            byte[] newSignature = crypto.byteStringToByteArray(res.getSignature());

            String newMessage = pubKey.toString() + newNonce + wid;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature)
                    && nonce + 1 == newNonce
                    && wid == myWid
            )
                receiveAmountResponses.add(res);

        });

        if (receiveAmountResponses.isEmpty())
            throw new DefaultErrorException();

    }


    public List<ProofOfWorkResponse> proofOfWork(ProofOfWorkRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> powWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (exceptions.responses.size() == this.byzantineQuorum)
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));


        return getProofOfWorkResponse(request.getNonce(), resCol);
    }

    private void powWorker(ProofOfWorkRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        while (true) {
            try {
                stubs.get(sName).withDeadlineAfter(10, TimeUnit.SECONDS)
                        .pow(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
                break;
            } catch (StatusRuntimeException sre) {
                hasDroppedOrThrowException(sre);
            }
        }
    }

    private List<ProofOfWorkResponse> getProofOfWorkResponse(long nonce, ResponseCollector resCol) {
        List<ProofOfWorkResponse> powResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            ProofOfWorkResponse res = (ProofOfWorkResponse) resCol.responses.get(sName);

            PublicKey key = crypto.bytesToKey(res.getPublicKey());
            long newNonce = res.getNonce();
            String serverName = res.getServerName();
            byte[] challenge = crypto.byteStringToByteArray(res.getChallenge());

            byte[] newSignature = crypto.byteStringToByteArray(res.getSignature());

            String newMessage = key.toString() + newNonce + serverName + Arrays.toString(challenge);

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature) && nonce + 1 == newNonce
            )
                powResponses.add(res);

        });

        if (powResponses.isEmpty())
            throw new DefaultErrorException();

        return powResponses;
    }


    public AuditResponse audit(AuditRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> auditWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (exceptions.responses.size() == this.byzantineQuorum) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));
        }

        return getAuditResponse(request.getNonce(), request.getRid(), resCol);
    }

    private void auditWorker(AuditRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        while (true) {
            try {
                stubs.get(sName).withDeadlineAfter(10, TimeUnit.SECONDS)
                        .audit(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
                break;
            } catch (StatusRuntimeException sre) {
                hasDroppedOrThrowException(sre);
            }
        }
    }

    private AuditResponse getAuditResponse(long nonce, int myRid, ResponseCollector resCol) {
        List<AuditResponse> auditResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            AuditResponse res = (AuditResponse) resCol.responses.get(sName);

            List<Transaction> transactions = res.getTransactionsList();
            long newNonce = res.getNonce();
            int rid = res.getRid();
            List<AdebProof> adebProofs = res.getAdebProofsList();
            byte[] newSignature = crypto.byteStringToByteArray(res.getSignature());

            String newMessage = transactions.toString() + newNonce + adebProofs + rid;

            int wid = transactions.isEmpty() ? 0 : transactions.get(transactions.size() - 1).getWid();

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature)
                    && nonce + 1 == newNonce
                    && myRid == rid
                    && validateAdebProof(adebProofs, wid)
                    && validateTransactions(transactions, false)
            )
                auditResponses.add(res);

        });

        if (auditResponses.isEmpty())
            throw new DefaultErrorException();

        return auditResponses.get(0);
    }


    public void checkAccountWriteBack(CheckAccountWriteBackRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> checkAccountWriteBackWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (exceptions.responses.size() == this.byzantineQuorum) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));
        }

        getCheckAccountWriteBackResponse(request.getNonce(), resCol);
    }

    private void checkAccountWriteBackWorker(CheckAccountWriteBackRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        while (true) {
            try {
                stubs.get(sName).withDeadlineAfter(10, TimeUnit.SECONDS)
                        .checkAccountWriteBack(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
                break;
            } catch (StatusRuntimeException sre) {
                hasDroppedOrThrowException(sre);
            }
        }
    }

    private void getCheckAccountWriteBackResponse(long nonce, ResponseCollector resCol) {
        List<CheckAccountWriteBackResponse> checkAccountWriteBackResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            CheckAccountWriteBackResponse res = (CheckAccountWriteBackResponse) resCol.responses.get(sName);

            PublicKey publicKey = crypto.bytesToKey(res.getPublicKey());
            long newNonce = res.getNonce();
            byte[] newSignature = crypto.byteStringToByteArray(res.getSignature());

            String newMessage = publicKey.toString() + newNonce;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature)
                    && nonce + 1 == newNonce
            )
                checkAccountWriteBackResponses.add(res);

        });

        if (checkAccountWriteBackResponses.isEmpty())
            throw new DefaultErrorException();
    }


    public void auditWriteBack(AuditWriteBackRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> auditWriteBackWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (exceptions.responses.size() == this.byzantineQuorum) {
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));
        }

        getAuditWriteBackResponse(request.getNonce(), resCol);
    }

    private void auditWriteBackWorker(AuditWriteBackRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        while (true) {
            try {
                stubs.get(sName).withDeadlineAfter(10, TimeUnit.SECONDS)
                        .auditWriteBack(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
                break;
            } catch (StatusRuntimeException sre) {
                hasDroppedOrThrowException(sre);
            }
        }
    }

    private void getAuditWriteBackResponse(long nonce, ResponseCollector resCol) {
        List<AuditWriteBackResponse> auditWriteBackResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            AuditWriteBackResponse res = (AuditWriteBackResponse) resCol.responses.get(sName);

            PublicKey publicKey = crypto.bytesToKey(res.getPublicKey());
            long newNonce = res.getNonce();
            byte[] newSignature = crypto.byteStringToByteArray(res.getSignature());

            String newMessage = publicKey.toString() + newNonce;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature)
                    && nonce + 1 == newNonce
            )
                auditWriteBackResponses.add(res);

        });

        if (auditWriteBackResponses.isEmpty())
            throw new DefaultErrorException();
    }


    public RidResponse getRid(RidRequest request) {

        ResponseCollector resCol = new ResponseCollector();
        ResponseCollector exceptions = new ResponseCollector();
        CountDownLatch finishLatch = new CountDownLatch(byzantineQuorum);

        this.stubs.keySet().forEach( sName -> getRidWorker(request, resCol, exceptions, finishLatch, sName) );

        await(finishLatch);

        checkServerStatus(resCol, exceptions);

        if (exceptions.responses.size() == this.byzantineQuorum)
            throw new StatusRuntimeException(Status.INTERNAL.withDescription(exceptionsHandler(exceptions, request.getNonce())));


        return getRidResponse(request.getNonce(), resCol);
    }

    private void getRidWorker(RidRequest request, ResponseCollector resCol, ResponseCollector exceptions, CountDownLatch finishLatch, String sName) {
        while (true) {
            try {
                stubs.get(sName).withDeadlineAfter(10, TimeUnit.SECONDS)
                        .getRid(request, new ClientObserver<>(resCol, exceptions, finishLatch, sName));
                break;
            } catch (StatusRuntimeException sre) {
                hasDroppedOrThrowException(sre);
            }
        }
    }

    private RidResponse getRidResponse(long nonce, ResponseCollector resCol) {
        List<RidResponse> ridResponses = new ArrayList<>();

        resCol.responses.keySet().forEach(sName -> {

            RidResponse res = (RidResponse) resCol.responses.get(sName);

            PublicKey key = crypto.bytesToKey(res.getPublicKey());
            long newNonce = res.getNonce();
            int rid = res.getRid();

            byte[] newSignature = crypto.byteStringToByteArray(res.getSignature());

            String newMessage = key.toString() + newNonce + rid;

            if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature) && nonce + 1 == newNonce)
                ridResponses.add(res);

        });

        if (ridResponses.isEmpty())
            throw new DefaultErrorException();

        return Collections.max(ridResponses, Comparator.comparing(RidResponse::getRid));

    }


    // aux
    private void hasDroppedOrThrowException(StatusRuntimeException sre) {
        if (sre.getStatus().getCode() == Status.DEADLINE_EXCEEDED.getCode())
            System.out.println("Request dropped.\nResending...");

        throw sre;
    }

    private boolean validateAdebProof(List<AdebProof> adebProofs, int wid) {

        for (AdebProof adebProof : adebProofs) {

            PublicKey publicKey = crypto.bytesToKey(adebProof.getPublicKey());
            String message = adebProof.getMessage();
            byte[] signature = crypto.byteStringToByteArray(adebProof.getSignature());

            if (!crypto.validateMessage(publicKey, message, signature) && adebProof.getWid() != wid)
                return false;
        }

        return true;
    }

    private boolean hasDuplicatedTransactions(List<Transaction> t) {

        final Set<Transaction> setToReturn = new HashSet<>();
        final Set<Transaction> set1 = new HashSet<>();

        for (Transaction yourInt : t) {
            if (!set1.add(yourInt)) {
                setToReturn.add(yourInt);
            }
        }

        return !setToReturn.isEmpty();
    }

    private boolean validateTransactions(List<Transaction> transactions, boolean isPending) {

        int checkWid = 1;

        for (Transaction transaction : transactions) {

            int amount = transaction.getAmount();
            String senderName = transaction.getSenderUsername();
            String receiverName = transaction.getReceiverUsername();
            PublicKey senderKey = crypto.bytesToKey(transaction.getSenderKey());
            PublicKey receiverKey = crypto.bytesToKey(transaction.getReceiverKey());
            int wid = transaction.getWid();
            boolean isSent = transaction.getSent();
            byte[] newSignature = crypto.byteStringToByteArray(transaction.getSignature());

            String newMessage = amount + senderName + receiverName + senderKey + receiverKey + wid + isSent;

            PublicKey key = isSent ? senderKey : receiverKey;

            if (!crypto.validateMessage(key, newMessage, newSignature))
                return false;

            if (!isPending && checkWid != wid)
                return false;

            checkWid++;
        }

        return true;
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
                    byte[] signature = crypto.byteStringToByteArray(errorResponse.getSignature());

                    String message = errorMsg + newNonce;

                    if (crypto.validateMessage(crypto.getPublicKey(sName), message, signature) && nonce + 1 == newNonce) {
                        exceptionResponses.add(errorMsg);
                    }
                }
            }
        });

        if (exceptionResponses.isEmpty())
            throw new DefaultErrorException();

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
