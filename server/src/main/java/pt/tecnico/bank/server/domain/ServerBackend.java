package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.domain.adeb.AdebInstance;
import pt.tecnico.bank.server.domain.adeb.AdebManager;
import pt.tecnico.bank.server.domain.adeb.MyAdebProof;
import pt.tecnico.bank.server.domain.exceptions.ErrorMessage;
import pt.tecnico.bank.server.domain.exceptions.ServerStatusRuntimeException;
import pt.tecnico.bank.server.grpc.Adeb.EchoRequest;
import pt.tecnico.bank.server.grpc.Adeb.ReadyRequest;
import pt.tecnico.bank.server.grpc.Server.*;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
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

        if (!crypto.validateMessage(key, initWid + String.valueOf(initBalance), pairSig))
            throwError(INVALID_SIGNATURE, 0);

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

        if (!crypto.validateMessage(senderKey, wid + String.valueOf(balance), pairSig))
            throwError(INVALID_SIGNATURE, nonce + 1);

        validateTransaction(transaction, nonce);

        String m = transaction.toString() + nonce + timestamp + wid + balance + Arrays.toString(pairSig);

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (!crypto.validateMessage(senderKey, m, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);

        // ------------------------ ADEB ------------------------

        AdebInstance adebInstance = new AdebInstance(this.nByzantineServers);

        adebManager.addInstance(Arrays.toString(sig), adebInstance);

        List<AdebProof> adebProofs = runAdeb(sig, adebInstance);

        // ------------------------------------------------------


        User sourceUser = users.get(senderKey);

        if(!validateUserNonce(sourceUser, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        if (sourceUser.getBalance() < transaction.getAmount())
            throwError(NOT_ENOUGH_BALANCE, nonce + 1);

        if (!(wid == sourceUser.getWid() + 1 && balance == sourceUser.getBalance() - transaction.getAmount()))
            throwError(BYZANTINE_CLIENT, nonce + 1);

        addPendingTransaction(
                transaction.getAmount(),
                transaction.getSenderUsername(),
                transaction.getReceiverUsername(),
                senderKey,
                receiverKey,
                wid,
                transaction.getSent(),
                crypto.byteStringToByteArray(transaction.getSignature())
        );  // add to dest pending list

        List<MyTransaction> totalTransactions = sourceUser.getTotalTransactions();
        totalTransactions.add(new MyTransaction(
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

        List<MyAdebProof> myAdebProofs = convertToMyAdebProofs(adebProofs, wid);
        sourceUser.setAdebProofs(myAdebProofs);

        sourceUser.setTotalTransactions(totalTransactions);
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
            ByteString clientKey, ByteString checkKey, long nonce, long timestamp, int rid, ByteString signature) {

        PublicKey cliKey = crypto.bytesToKey(clientKey);
        PublicKey chKey = crypto.bytesToKey(checkKey);

        if (!(users.containsKey(cliKey)) || !(users.containsKey(chKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        if(!validateUserNonce(users.get(cliKey), nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        byte[] sig = crypto.byteStringToByteArray(signature);
        String m = cliKey.toString() + chKey + nonce + timestamp + rid;

        if (!crypto.validateMessage(cliKey, m, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);

        User user = users.get(cliKey);

        if (rid != user.getRid() + 1)
            throwError(BYZANTINE_CLIENT, nonce + 1);

        user.setRid(rid);
        users.put(cliKey, user);
        stateManager.saveState(users);

        List<Transaction> pendingTransactions = getPendingTransactions(chKey);

        int wid = users.get(chKey).getWid();
        int currBalance = users.get(chKey).getBalance();
        byte[] pairSig = users.get(chKey).getPairSignature();
        List<MyAdebProof> myAdebProofs = users.get(chKey).getAdebProofs();

        List<AdebProof> adebProofs = convertToAdebProofs(myAdebProofs);

        String messageToSign = pendingTransactions.toString() + (nonce + 1) + adebProofs + rid + currBalance + wid + Arrays.toString(pairSig);

        return CheckAccountResponse.newBuilder()
                .addAllPendingTransactions(pendingTransactions)
                .setNonce(nonce + 1)
                .addAllAdebProofs(adebProofs)
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

        if (!crypto.validateMessage(pubKey, wid + String.valueOf(balance), pairSig))
            throwError(INVALID_SIGNATURE, nonce + 1);

        transactions.forEach(transaction -> validateTransaction(transaction, nonce));

        String message = transactions + pubKey.toString() + nonce + timestamp + wid + balance + Arrays.toString(pairSig);

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (!crypto.validateMessage(pubKey, message, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);


        // ------------------------ ADEB ------------------------

        AdebInstance adebInstance = new AdebInstance(this.nByzantineServers);

        adebManager.addInstance(Arrays.toString(sig), adebInstance);

        List<AdebProof> adebProofs = runAdeb(sig, adebInstance);

        // ------------------------------------------------------


        User user = users.get(pubKey);

        if(!validateUserNonce(user, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        List<MyTransaction> pendingTransactions = user.getPendingTransactions();
        int amountToReceive = 0;

        for (MyTransaction pendingTransaction : pendingTransactions)
            amountToReceive += pendingTransaction.getAmount();

        if (!(wid > user.getWid() && balance == user.getBalance() + amountToReceive))
            throwError(BYZANTINE_CLIENT, nonce + 1);


        transactions.forEach(transaction -> transactionAmount(
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
        user.setPendingTransactions(pendingTransactions); // clear the list

        user.setWid(wid);
        user.setPairSignature(pairSig);

        List<MyAdebProof> myAdebProofs = convertToMyAdebProofs(adebProofs, wid);
        user.setAdebProofs(myAdebProofs);

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

    public AuditResponse audit(
            ByteString clientKey, ByteString auditKey, long nonce, long timestamp, Map<String, Long> pows, int rid, ByteString signature
    )  {

        PublicKey cliKey = crypto.bytesToKey(clientKey);
        PublicKey auKey = crypto.bytesToKey(auditKey);

        if (!(users.containsKey(cliKey)) || !(users.containsKey(auKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        if(!validateUserNonce(users.get(cliKey), nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        byte[] sig = crypto.byteStringToByteArray(signature);
        String m = cliKey.toString() + auKey + nonce + timestamp + pows + rid;

        if (!crypto.validateMessage(cliKey, m, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);


        // --------------------- Proof of work ---------------------

        User user = users.get(cliKey);

        if (rid != user.getRid() + 1)
            throwError(BYZANTINE_CLIENT, nonce + 1);

        if (pows != null && pows.get(this.sName) != null && !verifyProofOfWork(user, pows.get(this.sName)))
            throwError(INVALID_POW, nonce + 1);

        user.setRid(rid);
        users.put(cliKey, user);
        stateManager.saveState(users);

        // ----------------------------------------------------------

        List<Transaction> transactions = getTotalTransactions(auKey);
        List<MyAdebProof> myAdebProofs = users.get(auKey).getAdebProofs();

        List<AdebProof> adebProofs = convertToAdebProofs(myAdebProofs);

        String messageToSign = transactions.toString() + (nonce + 1) + adebProofs + rid;

        return AuditResponse.newBuilder()
                .addAllTransactions(transactions)
                .setNonce(nonce + 1)
                .addAllAdebProofs(adebProofs)
                .setRid(rid)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, messageToSign)))
                .build();
    }

    public ProofOfWorkResponse generateProofOfWork(ByteString publicKey, long nonce, long timestamp, ByteString signature) {

        PublicKey cliKey = crypto.bytesToKey(publicKey);

        if (!(users.containsKey(cliKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        User user = users.get(cliKey);

        if(!validateUserNonce(user, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        String message = cliKey.toString() + nonce + timestamp;

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (!crypto.validateMessage(cliKey, message, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);


        byte[] array = new byte[7]; // length is bounded by 7
        new Random().nextBytes(array);
        String challenge = new String(array, StandardCharsets.UTF_8);

        byte[] hashChallenge = crypto.encrypt(this.sName, challenge);

        user.addChallenge(challenge);
        stateManager.saveState(users);


        message = cliKey.toString() + (nonce + 1) + this.sName + Arrays.toString(hashChallenge);

        return ProofOfWorkResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(cliKey.getEncoded()))
                .setNonce(nonce + 1)
                .setServerName(this.sName)
                .setChallenge(ByteString.copyFrom(hashChallenge))
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, message)))
                .build();
    }

    public CheckAccountWriteBackResponse checkAccountWriteBack(
            ByteString clientKey, ByteString checkKey, long nonce, long timestamp, List<Transaction> pendingTransactions,
            int balance, int wid, ByteString pairSign, ByteString signature
    ) {

        PublicKey cliKey = crypto.bytesToKey(clientKey);
        PublicKey chKey = crypto.bytesToKey(checkKey);

        if (!(users.containsKey(cliKey)) || !(users.containsKey(chKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        if (!validateUserNonce(users.get(cliKey), nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        byte[] pairSig = crypto.byteStringToByteArray(pairSign);

        if (!crypto.validateMessage(chKey, wid + String.valueOf(balance), pairSig))
            throwError(INVALID_SIGNATURE, nonce + 1);

        String message = cliKey.toString() + chKey + nonce + timestamp + pendingTransactions
                + balance + wid + Arrays.toString(pairSig);

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (!crypto.validateMessage(cliKey, message, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);


        // ------------------------ ADEB ------------------------

        AdebInstance adebInstance = new AdebInstance(this.nByzantineServers);

        adebManager.addInstance(Arrays.toString(sig), adebInstance);

        List<AdebProof> adebProofs = runAdeb(sig, adebInstance);

        // ------------------------------------------------------

        User checkUser = users.get(chKey);

        if (wid > checkUser.getWid()) {
            checkUser.setBalance(balance);
            checkUser.setWid(wid);
            checkUser.setPairSignature(crypto.byteStringToByteArray(pairSign));
            checkUser.setPendingTransactions(transactionsToTransactions(pendingTransactions));

            List<MyAdebProof> myAdebProofs = convertToMyAdebProofs(adebProofs, wid);
            checkUser.setAdebProofs(myAdebProofs);

            users.put(chKey, checkUser);
            stateManager.saveState(users);
        }

        return CheckAccountWriteBackResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(cliKey.getEncoded()))
                .setNonce(nonce + 1)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, cliKey.toString() + (nonce + 1))))
                .build();
    }

    public AuditWriteBackResponse auditWriteBack(
            ByteString clientKey, ByteString auditKey, long nonce, long timestamp, List<Transaction> transactions, ByteString signature
    ) {

        PublicKey cliKey = crypto.bytesToKey(clientKey);
        PublicKey auKey = crypto.bytesToKey(auditKey);

        if (!(users.containsKey(cliKey)) || !(users.containsKey(auKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        if (!validateUserNonce(users.get(cliKey), nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        String message = cliKey.toString() + auKey + nonce + timestamp + transactions;

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (!crypto.validateMessage(cliKey, message, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);

        if (!validateTransactions(transactions, nonce + 1))
            throwError(BYZANTINE_CLIENT, nonce + 1);

        // ------------------------ ADEB ------------------------

        AdebInstance adebInstance = new AdebInstance(this.nByzantineServers);

        adebManager.addInstance(Arrays.toString(sig), adebInstance);

        List<AdebProof> adebProofs = runAdeb(sig, adebInstance);

        // ------------------------------------------------------

        User checkUser = users.get(auKey);

        int wid = transactions.isEmpty() ? 0 : transactions.get(transactions.size() - 1).getWid();

        if (wid > users.get(cliKey).getWid()) {

            checkUser.setTotalTransactions(transactionsToTransactions(transactions));

            List<MyAdebProof> myAdebProofs = convertToMyAdebProofs(adebProofs, wid);
            checkUser.setAdebProofs(myAdebProofs);

            users.put(auKey, checkUser);
            stateManager.saveState(users);
        }


        return AuditWriteBackResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(cliKey.getEncoded()))
                .setNonce(nonce + 1)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, cliKey.toString() + (nonce + 1))))
                .build();
    }

    public RidResponse getRid(ByteString publicKey, long nonce, long timestamp, ByteString signature) {
        PublicKey cliKey = crypto.bytesToKey(publicKey);

        if (!(users.containsKey(cliKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        User user = users.get(cliKey);

        if(!validateUserNonce(user, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        String message = cliKey.toString() + nonce + timestamp;

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (!crypto.validateMessage(cliKey, message, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);


        int rid = user.getRid();

        message = cliKey.toString() + (nonce + 1) + rid;

        return RidResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(cliKey.getEncoded()))
                .setNonce(nonce + 1)
                .setRid(rid)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, message)))
                .build();
    }

    // ----------------------------------- ADEB -------------------------------------


    /* On echo request receive:
     * check signature/nonce
     * check if recvInput == myInput
     *     if true -> add echo in echos
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
     *  check if readys.size > f and sentReady = false
     *       if true -> send readys
     *  else check if readys.size > 2f and sentReady = true and deliver = false
     *       if true -> deliver
     */

    public void ready(ByteString pubKeyString, String sName, ByteString input, long nonce, long ts, ByteString signature) {

        System.out.println("Received ready from server " + sName);

        byte[] inputByte = crypto.byteStringToByteArray(input);

        AdebProof adebProof = doAdebVerifications(pubKeyString, sName, inputByte, nonce, ts, signature);

        AdebInstance adebInstance = adebManager.getOrAddAdebInstance(Arrays.toString(inputByte));

        if (Arrays.equals(inputByte, adebInstance.getInput())) {

            System.out.println("The ready input from server " + sName + " is the same as mine.");

            adebInstance.addAdebProof(adebProof);
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


    private List<AdebProof> runAdeb(byte[] clientInput, AdebInstance adebInstance) {

        List<AdebProof> adebProofs = new LinkedList<>();

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

            adebProofs = adebInstance.getAdebProof();
            //adebInstance.getAdebFrontend().close();
            this.adebManager.removeAdebInstance(Arrays.toString(clientInput));
        }

        return adebProofs;
    }

    // ------------------------------------ AUX -------------------------------------

    private void transactionAmount(int amount, String senderName, String receiverName, PublicKey sourceKey, PublicKey destKey, int  wid, boolean sent, byte[] signature) {
        User user = users.get(destKey);

        List<MyTransaction> totalTransactions = user.getTotalTransactions();
        totalTransactions.add(new MyTransaction(amount, senderName, receiverName, sourceKey, destKey, wid, sent, signature));

        user.setTotalTransactions(totalTransactions);
        user.setBalance(user.getBalance() + amount);
        users.put(destKey, user);
    }

    private void addPendingTransaction(int amount, String senderName, String receiverName, PublicKey sourceKey, PublicKey destKey, int wid, boolean sent, byte[] signature) {
        User user = users.get(destKey);

        List<MyTransaction> destPendingMyTransactions = user.getPendingTransactions();
        destPendingMyTransactions.add(new MyTransaction(amount, senderName, receiverName, sourceKey, destKey, wid, sent, signature)); // added to the dest pending transactions list

        user.setPendingTransactions(destPendingMyTransactions);
        users.put(destKey, user);
    }


    private List<Transaction> getPendingTransactions(PublicKey key) {
        List<Transaction> transactions = new ArrayList<>();
        users.get(key).getPendingTransactions().forEach(transaction -> transactions.add(buildTransaction(transaction)));
        return transactions;
    }

    private List<Transaction> getTotalTransactions(PublicKey key) {
        List<Transaction> transactions = new ArrayList<>();
        users.get(key).getTotalTransactions().forEach(transaction -> transactions.add(buildTransaction(transaction)));
        return transactions;
    }

    private Transaction buildTransaction(MyTransaction transaction) {
        return Transaction.newBuilder()
                .setAmount(transaction.getAmount())
                .setSenderUsername(transaction.getSenderName())
                .setReceiverUsername(transaction.getReceiverName())
                .setSenderKey(ByteString.copyFrom(transaction.getSenderKey().getEncoded()))
                .setReceiverKey(ByteString.copyFrom(transaction.getReceiverKey().getEncoded()))
                .setWid(transaction.getWid())
                .setSent(transaction.isSent())
                .setSignature(ByteString.copyFrom(transaction.getSignature()))
                .build();
    }

    private LinkedList<MyTransaction> transactionsToTransactions(List<Transaction> transactions) {

        LinkedList<MyTransaction> myTransactions = new LinkedList<>();

        for (Transaction transaction : transactions) {
            myTransactions.add(new MyTransaction(
                    transaction.getAmount(),
                    transaction.getSenderUsername(),
                    transaction.getReceiverUsername(),
                    crypto.bytesToKey(transaction.getSenderKey()),
                    crypto.bytesToKey(transaction.getReceiverKey()),
                    transaction.getWid(),
                    transaction.getSent(),
                    crypto.byteStringToByteArray(transaction.getSignature())
            ));
        }

        return myTransactions;
    }

    private List<MyAdebProof> convertToMyAdebProofs(List<AdebProof> adebProofs, int wid) {
        List<MyAdebProof> myAdebProofs = Collections.synchronizedList(new ArrayList<>());

        for (AdebProof adebProof : adebProofs)
            myAdebProofs.add(
                    new MyAdebProof(
                            crypto.bytesToKey(adebProof.getPublicKey()),
                            adebProof.getMessage(),
                            wid,
                            crypto.byteStringToByteArray(adebProof.getSignature())
                    )
            );

        return myAdebProofs;
    }

    private List<AdebProof> convertToAdebProofs(List<MyAdebProof> myAdebProofs) {
        List<AdebProof> adebProofs = Collections.synchronizedList(new ArrayList<>());

        for (MyAdebProof adebProof : myAdebProofs)
            adebProofs.add(
                    AdebProof.newBuilder()
                            .setPublicKey(ByteString.copyFrom(adebProof.getServerKey().getEncoded()))
                            .setMessage(adebProof.getMessage())
                            .setWid(adebProof.getWid())
                            .setSignature(ByteString.copyFrom(adebProof.getSignature()))
                            .build()
            );

        return adebProofs;
    }

    private boolean validateUserNonce(User user, long nonce, long timestamp) {
        return user.getNonceManager().validateNonce(nonce, timestamp);
    }

    private boolean validateServerNonce(long nonce, long timestamp) {
        return this.nonceManager.validateNonce(nonce, timestamp);
    }

    private boolean validateTransactions(List<Transaction> transactions, long nonce) {

        int checkWid = 1;

        for (Transaction transaction : transactions) {

            validateTransaction(transaction, nonce);

            if (checkWid != transaction.getWid())
                return false;

            checkWid++;
        }

        return true;
    }

    private void validateTransaction(Transaction transaction, long nonce) {
        PublicKey senderKey = crypto.bytesToKey(transaction.getSenderKey());
        PublicKey receiverKey = crypto.bytesToKey(transaction.getReceiverKey());

        String tm = transaction.getAmount() + transaction.getSenderUsername() + transaction.getReceiverUsername()
                + senderKey + receiverKey + transaction.getWid() + transaction.getSent();

        byte[] transactionSig = crypto.byteStringToByteArray(transaction.getSignature());

        PublicKey key = transaction.getSent() ? senderKey : receiverKey;

        if (!crypto.validateMessage(key, tm, transactionSig))
            throwError(INVALID_SIGNATURE, nonce + 1);
    }

    public boolean verifyProofOfWork(User u, long pow) {
        long now = System.currentTimeMillis() / 1000;

        for (String challenge : u.getChallenges().keySet()) {

            long challengeTs = u.getChallenges().get(challenge);
            byte[] challengeHash = crypto.encrypt(this.sName, challenge);

            if (now - challengeTs < 600 && crypto.verifyProofOfWork(challengeHash, pow)) // 10 minute gap
                return true;
        }

        return false;
    }

    private AdebProof doAdebVerifications(ByteString pubKeyString, String sName, byte[] input, long nonce, long ts, ByteString signature) {
        PublicKey pubKey = crypto.bytesToKey(pubKeyString);

        String newMessage = pubKey.toString() + sName + Arrays.toString(input) + nonce + ts;

        byte[] sig = crypto.byteStringToByteArray(signature);

        if  (!crypto.validateMessage(pubKey, newMessage, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);

        if (!validateServerNonce(nonce, ts))
            throwError(INVALID_NONCE, nonce + 1);

        return AdebProof.newBuilder().setPublicKey(pubKeyString).setMessage(newMessage).setSignature(signature).build();
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
