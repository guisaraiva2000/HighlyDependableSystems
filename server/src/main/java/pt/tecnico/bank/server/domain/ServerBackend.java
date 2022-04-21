package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.domain.adeb.AdebInstance;
import pt.tecnico.bank.server.domain.adeb.AdebManager;
import pt.tecnico.bank.server.domain.exceptions.ErrorMessage;
import pt.tecnico.bank.server.domain.exceptions.ServerStatusRuntimeException;
import pt.tecnico.bank.server.grpc.Adeb.EchoRequest;
import pt.tecnico.bank.server.grpc.Adeb.ReadyRequest;
import pt.tecnico.bank.server.grpc.Server.*;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import static pt.tecnico.bank.server.domain.exceptions.ErrorMessage.*;

/**
 * Facade class.
 * Contains the service operations.
 */
public class ServerBackend implements Serializable {

    private final ConcurrentHashMap<PublicKey, User> users;
    private final StateManager stateManager;
    private final Crypto crypto;
    private final String sName;
    private final int nByzantineServers;

    private final NonceManager nonceManager = new NonceManager();

    private final AdebManager adebManager;


    public ServerBackend(String sName, int nByzantineServers) {
        this.sName = sName;
        this.nByzantineServers = nByzantineServers;
        this.stateManager = new StateManager(sName);

        this.crypto = new Crypto(sName, sName, false);
        this.users = stateManager.loadState();

        this.adebManager = new AdebManager(nByzantineServers);

        initServerKeys();
    }

    public synchronized OpenAccountResponse openAccount (
            String username, int initWid, int initBalance, ByteString pairSignature, ByteString pubKey, ByteString signature
    ) {

        PublicKey key = crypto.bytesToKey(pubKey);

        if (users.containsKey(key))
            throwError(ACCOUNT_ALREADY_EXISTS, 0);

        byte[] pairSig = crypto.byteStringToByteArray(pairSignature);

        String message = username + initWid + initBalance + Arrays.toString(pairSig) + key.toString();

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (!crypto.validateMessage(key, message, sig))
            throwError(INVALID_SIGNATURE, 0);

        User newUser = new User(key, username, initWid, initBalance, pairSig);
        users.put(key, newUser);

        stateManager.saveState(users);

        return OpenAccountResponse.newBuilder()
                .setUsername(username)
                .setPublicKey(ByteString.copyFrom(key.getEncoded()))
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, username + key)))
                .build();
    }

    public SendAmountResponse sendAmount(
            Transaction transaction, long nonce, long timestamp, int balance, ByteString pairSignature, ByteString signature
    ) {

        PublicKey senderKey = crypto.bytesToKey(transaction.getSenderKey());
        PublicKey receiverKey = crypto.bytesToKey(transaction.getReceiverKey());

        if (senderKey.equals(receiverKey)) {
            throwError(SAME_ACCOUNT, nonce + 1);
        } else if (!(users.containsKey(senderKey) && users.containsKey(receiverKey))) {
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);
        } else if (transaction.getAmount() < 0) {
            throwError(INVALID_BALANCE, nonce + 1);
        }

        int wid = transaction.getWid();
        byte[] pairSig = crypto.byteStringToByteArray(pairSignature);

        String m = transaction.toString() + nonce + timestamp + wid + balance + Arrays.toString(pairSig);

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (!crypto.validateMessage(senderKey, m, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);

        AdebInstance adebInstance = new AdebInstance(this.nByzantineServers);

        adebManager.addInstance(Arrays.toString(sig), adebInstance);

        runAdeb(sig, adebInstance);   // ADEB!!!!


        User sourceUser = users.get(senderKey);

        if(!validateUserNonce(sourceUser, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        if (sourceUser.getBalance() < transaction.getAmount())
            throwError(NOT_ENOUGH_BALANCE, nonce + 1);

        if (!(wid > sourceUser.getWid() && balance == sourceUser.getBalance() - transaction.getAmount()))
            throwError(BYZANTINE_CLIENT, nonce + 1);

        addPendingTransfer(
                transaction.getAmount(),
                transaction.getSenderUsername(),
                transaction.getReceiverUsername(),
                senderKey,
                receiverKey,
                wid,
                transaction.getSent(),
                crypto.byteStringToByteArray(transaction.getSignature())
        );  // add to dest pending list

        LinkedList<Transfer> totalTransfers = sourceUser.getTotalTransfers();
        totalTransfers.add(new Transfer(
                    transaction.getAmount(),
                    transaction.getSenderUsername(),
                    transaction.getReceiverUsername(),
                    crypto.bytesToKey(transaction.getSenderKey()),
                    crypto.bytesToKey(transaction.getReceiverKey()),
                    transaction.getWid(),
                    transaction.getSent(),
                    crypto.byteStringToByteArray(transaction.getSignature())
                )
        );

        sourceUser.setTotalTransfers(totalTransfers);
        sourceUser.setBalance(balance);
        sourceUser.setWid(wid);
        sourceUser.setPairSignature(pairSig);
        users.put(senderKey, sourceUser);

        stateManager.saveState(users);

        String messageToSign = senderKey.toString() + (nonce + 1) + wid;

        return SendAmountResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(senderKey.getEncoded()))
                .setNonce(nonce + 1)
                .setWid(wid)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, messageToSign)))
                .build();

    }

    public CheckAccountResponse checkAccount(
            ByteString clientKey, ByteString checkKey, long nonce, long timestamp, int rid
    ) {
        PublicKey cliKey = crypto.bytesToKey(clientKey);
        PublicKey chKey = crypto.bytesToKey(checkKey);

        if (!(users.containsKey(cliKey)) || !(users.containsKey(chKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        if(!validateUserNonce(users.get(cliKey), nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        List<Transaction> pendingTransactions = getPendingTransactions(chKey);

        int wid = users.get(chKey).getWid();
        int currBalance = users.get(chKey).getBalance();
        byte[] pairSig = users.get(chKey).getPairSignature();

        String messageToSign = pendingTransactions.toString() + (nonce + 1) + rid + currBalance + wid + Arrays.toString(pairSig);

        return CheckAccountResponse.newBuilder()
                .addAllPendingTransactions(pendingTransactions)
                .setNonce(nonce + 1)
                .setRid(rid)
                .setBalance(currBalance)
                .setWid(wid)
                .setPairSignature(ByteString.copyFrom(pairSig))
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, messageToSign)))
                .build();
    }

    public ReceiveAmountResponse receiveAmount(
            List<Transaction> transactions, ByteString publicKey, long nonce, long timestamp, int wid, int balance, ByteString pairSignature, ByteString signature
    ) {

        PublicKey pubKey = crypto.bytesToKey(publicKey);

        if (!(users.containsKey(pubKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        byte[] pairSig = crypto.byteStringToByteArray(pairSignature);

        String message = transactions + pubKey.toString() + nonce + timestamp + wid + balance + Arrays.toString(pairSig);

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (!crypto.validateMessage(pubKey, message, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);


        AdebInstance adebInstance = new AdebInstance(this.nByzantineServers);

        adebManager.addInstance(Arrays.toString(sig), adebInstance);

        runAdeb(sig, adebInstance);   // ADEB!!!!


        User user = users.get(pubKey);

        if(!validateUserNonce(user, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        LinkedList<Transfer> pendingTransactions = user.getPendingTransfers();
        int amountToReceive = 0;

        for (Transfer pendingTransaction : pendingTransactions)
            amountToReceive += pendingTransaction.getAmount();

        if (!(wid > user.getWid() && balance == user.getBalance() + amountToReceive))
            throwError(BYZANTINE_CLIENT, nonce + 1);


        transactions.forEach(transaction -> transferAmount(
                transaction.getAmount(),
                transaction.getSenderUsername(),
                transaction.getReceiverUsername(),
                crypto.bytesToKey(transaction.getSenderKey()),
                crypto.bytesToKey(transaction.getReceiverKey()),
                transaction.getWid(),
                transaction.getSent(),
                crypto.byteStringToByteArray(transaction.getSignature())
        ));

        pendingTransactions.clear();
        user.setPendingTransfers(pendingTransactions); // clear the list

        user.setWid(wid);
        user.setPairSignature(pairSig);

        users.put(pubKey, user); // update user

        stateManager.saveState(users);

        String newMessage = String.valueOf(pubKey) + (nonce + 1) + wid;

        return ReceiveAmountResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(pubKey.getEncoded()))
                .setNonce(nonce + 1)
                .setWid(wid)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, newMessage)))
                .build();
    }

    public AuditResponse audit(ByteString clientKey, ByteString auditKeyy, long nonce, long timestamp, int rid)  {
        PublicKey cliKey = crypto.bytesToKey(clientKey);
        PublicKey auditKey = crypto.bytesToKey(auditKeyy);

        if (!(users.containsKey(cliKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        User user = users.get(cliKey);

        if(!validateUserNonce(user, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        List<Transaction> transactions = getTotalTransactions(auditKey);

        String messageToSign = transactions.toString() + (nonce + 1) + rid;

        return AuditResponse.newBuilder()
                .addAllTransactions(transactions)
                .setNonce(nonce + 1)
                .setRid(rid)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, messageToSign)))
                .build();
    }

    // ----------------------------------- ADEB -------------------------------------


    /* On echo request receive:
     * check signature/nonce
     * check if recvInput == myInput
     *     if true -> add echo in echos
     *     else    -> do nothing ?
     * check if echos.size > (n+f)/2
     *     if true -> send readys
     */

    public void echo(ByteString pubKeyString, String sName, ByteString input, long nonce, long ts, ByteString signature) {

        System.out.println("Received echo from server " + sName);

        byte[] inputByte = crypto.byteStringToByteArray(input);

        doAdebVerifications(pubKeyString, sName, inputByte, nonce, ts, signature);

        AdebInstance adebInstance = adebManager.getOrAddAdebInstance(Arrays.toString(inputByte));

        if (Arrays.equals(inputByte, adebInstance.getInput())) {

            System.out.println("The echo input from server " + sName + " is the same as mine.");

            adebInstance.addEcho(inputByte);
        }

        PublicKey sKey = crypto.getPublicKey(this.sName);

        if (adebInstance.getEchos().size() == adebInstance.getByzantineEchoQuorum() && !adebInstance.isSentReady()) {

            System.out.println("\nSending readys from echo...");

            sendReadys(input, sKey, adebInstance);
        }
    }


    /* On ready request receive:
     *  check signature/nonce
     *  check if recvInput == myInput
     *       if true -> add ready in readys
     *       else    -> do nothing ?
     *  check if readys.size > f and sentReady = false
     *       if true -> send readys
     *  else check if readys.size > 2f and sentReady = true and deliver = false
     *       if true -> deliver
     */

    public void ready(ByteString pubKeyString, String sName, ByteString input, long nonce, long ts, ByteString signature) {

        System.out.println("Received ready from server " + sName);

        byte[] inputByte = crypto.byteStringToByteArray(input);

        doAdebVerifications(pubKeyString, sName, inputByte, nonce, ts, signature);

        AdebInstance adebInstance = adebManager.getOrAddAdebInstance(Arrays.toString(inputByte));

        if (Arrays.equals(inputByte, adebInstance.getInput())) {

            System.out.println("The ready input from server " + sName + " is the same as mine.");

            adebInstance.addReady(inputByte);
        }

        PublicKey sKey = crypto.getPublicKey(this.sName);

        if (adebInstance.getReadys().size() > this.nByzantineServers && !adebInstance.isSentReady()) {

            System.out.println("Sending readys...");

            sendReadys(input, sKey, adebInstance);

        } else if (adebInstance.getReadys().size() == adebInstance.getByzantineReadyQuorum()
                && adebInstance.isSentReady()
                && !adebInstance.isDelivered()) {

            adebInstance.setDelivered(true);
            adebInstance.countDown();
        }

    }

    private void sendReadys(ByteString input, PublicKey sKey, AdebInstance adebInstance) {

        adebInstance.setSentReady(true);

        long readyNonce = crypto.generateNonce();
        long readyTs = crypto.generateTimestamp();
        byte[] inputBytes = crypto.byteStringToByteArray(input);

        String message = sKey.toString() + this.sName + Arrays.toString(inputBytes) + readyNonce + readyTs;

        byte[] readySignature = crypto.encrypt(this.sName, message);

        adebInstance.getAdebFrontend().ready(
                ReadyRequest.newBuilder()
                        .setInput(ByteString.copyFrom(inputBytes))
                        .setNonce(readyNonce)
                        .setTimestamp(readyTs)
                        .setSname(this.sName)
                        .setKey(ByteString.copyFrom(sKey.getEncoded()))
                        .setSignature(ByteString.copyFrom(readySignature))
                        .build()
        );

    }


    private void runAdeb(byte[] clientInput, AdebInstance adebInstance) {

        if (!adebInstance.isSentEcho()) {

            System.out.println("Running ADEB...\n");
            adebInstance.setLatch(new CountDownLatch(1));

            adebInstance.setInput(clientInput);
            adebInstance.setSentEcho(true);

            // echo
            PublicKey pubKey = crypto.getPublicKey(this.sName);
            long echoNonce = crypto.generateNonce();
            long ts = crypto.generateTimestamp();

            String message = pubKey.toString() + this.sName + Arrays.toString(clientInput) + echoNonce + ts;

            byte[] signature = crypto.encrypt(this.sName, message);

            adebInstance.getAdebFrontend().echo(
                    EchoRequest.newBuilder()
                        .setInput(ByteString.copyFrom(clientInput))
                        .setNonce(echoNonce)
                        .setTimestamp(ts)
                        .setSname(this.sName)
                        .setKey(ByteString.copyFrom(pubKey.getEncoded()))
                        .setSignature(ByteString.copyFrom(signature)).build()
            );

            await(adebInstance.getLatch());
            System.out.println("ADEB ENDED!! All servers synchronized\n\n");

            this.adebManager.removeAdebInstance(Arrays.toString(clientInput));
        }

    }

    // ------------------------------------ AUX -------------------------------------

    private void transferAmount(int amount, String senderName, String receiverName, PublicKey sourceKey, PublicKey destKey, int  wid, boolean sent, byte[] signature) {
        User user = users.get(destKey);

        LinkedList<Transfer> totalTransfers = user.getTotalTransfers();
        totalTransfers.add(new Transfer(amount, senderName, receiverName, sourceKey, destKey, wid, sent, signature));

        user.setTotalTransfers(totalTransfers);
        user.setBalance(user.getBalance() + amount);
        users.put(destKey, user);
    }

    private void addPendingTransfer(int amount, String senderName, String receiverName, PublicKey sourceKey, PublicKey destKey, int wid, boolean sent, byte[] signature) {
        User user = users.get(destKey);
        LinkedList<Transfer> destPendingTransfers = user.getPendingTransfers();
        destPendingTransfers.add(new Transfer(amount, senderName, receiverName, sourceKey, destKey, wid, sent, signature)); // added to the dest pending transfers list
        user.setPendingTransfers(destPendingTransfers);
        users.put(destKey, user);
    }


    private List<Transaction> getPendingTransactions(PublicKey key) {
        List<Transaction> transactions = new ArrayList<>();
        users.get(key).getPendingTransfers().forEach(transfer -> transactions.add(buildTransaction(transfer)));
        return transactions;
    }

    private List<Transaction> getTotalTransactions(PublicKey key) {
        List<Transaction> transactions = new ArrayList<>();
        users.get(key).getTotalTransfers().forEach(transfer -> transactions.add(buildTransaction(transfer)));
        return transactions;
    }

    private Transaction buildTransaction(Transfer transfer) {
        return Transaction.newBuilder()
                .setAmount(transfer.getAmount())
                .setSenderUsername(transfer.getSenderName())
                .setReceiverUsername(transfer.getReceiverName())
                .setSenderKey(ByteString.copyFrom(transfer.getSenderKey().getEncoded()))
                .setReceiverKey(ByteString.copyFrom(transfer.getReceiverKey().getEncoded()))
                .setWid(transfer.getWid())
                .setSent(transfer.isSent())
                .setSignature(ByteString.copyFrom(transfer.getSignature()))
                .build();
    }

    private boolean validateUserNonce(User user, long nonce, long timestamp) {
        return user.getNonceManager().validateNonce(nonce, timestamp);
    }

    private boolean validateServerNonce(long nonce, long timestamp) {
        return this.nonceManager.validateNonce(nonce, timestamp);
    }

    private void doAdebVerifications(ByteString pubKeyString, String sName, byte[] input, long nonce, long ts, ByteString signature) {
        PublicKey pubKey = crypto.bytesToKey(pubKeyString);

        String newMessage = pubKey.toString() + sName + Arrays.toString(input) + nonce + ts;

        byte[] sig = crypto.byteStringToByteArray(signature);

        if  (!crypto.validateMessage(pubKey, newMessage, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);

        if (!validateServerNonce(nonce, ts))
            throwError(INVALID_NONCE, nonce + 1);
    }


    private void initServerKeys() {
        this.crypto.generateKeyStore(this.sName);
    }

    private void throwError(ErrorMessage errorMessage, long nonce) {
        throw new ServerStatusRuntimeException(
                Status.INTERNAL,
                errorMessage.label,
                nonce,
                crypto.encrypt(this.sName, errorMessage.label + nonce)
        );
    }

    public static void await(CountDownLatch finishLatch) {
        try {
            finishLatch.await();
        } catch (InterruptedException ignored) {
        }
    }

}
