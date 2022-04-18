package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.domain.adeb.AdebFrontend;
import pt.tecnico.bank.server.domain.exceptions.ErrorMessage;
import pt.tecnico.bank.server.domain.exceptions.ServerStatusRuntimeException;
import pt.tecnico.bank.server.grpc.Adeb.EchoRequest;
import pt.tecnico.bank.server.grpc.Adeb.ReadyRequest;
import pt.tecnico.bank.server.grpc.Server.*;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

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


    // ADEB
    private final AdebFrontend adebFrontend;
    private final int byzantineEchoQuorum;
    private final int byzantineReadyQuorum;
    private final int nByzantineServers;
    private byte[] input = null;
    private boolean sentEcho = false;
    private boolean sentReady = false;
    private boolean delivered = false;
    private final NonceManager nonceManager = new NonceManager();;
    private final List<byte[]> echos = new ArrayList<>();
    private final List<byte[]> readys = new ArrayList<>();
    private CountDownLatch latch;

    public ServerBackend(String sName, int nByzantineServers) {
        this.sName = sName;
        this.nByzantineServers = nByzantineServers;
        this.stateManager = new StateManager(sName);

        this.crypto = new Crypto(sName, sName, false);
        this.users = stateManager.loadState();

        this.adebFrontend = new AdebFrontend(nByzantineServers);

        int nServers = 3 * nByzantineServers + 1;
        this.byzantineEchoQuorum = (nServers + nByzantineServers) / 2 + 1;      //  > (N + f) / 2
        this.byzantineReadyQuorum = 2 * nByzantineServers + 1;                  //  > 2f

        initServerKeys();
    }

    public synchronized OpenAccountResponse openAccount(ByteString pubKey, ByteString signature) {

        PublicKey pubKeyBytes = crypto.bytesToKey(pubKey);

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (users.containsKey(pubKeyBytes))
            throwError(ACCOUNT_ALREADY_EXISTS, 0);

        String message = pubKeyBytes.toString();

        if (!crypto.validateMessage(pubKeyBytes, message, sig))
            throwError(INVALID_SIGNATURE, 0);

        User newUser = new User(pubKeyBytes, 100);
        users.put(pubKeyBytes, newUser);

        stateManager.saveState(users);

        return OpenAccountResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(pubKeyBytes.getEncoded()))
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, String.valueOf(pubKeyBytes))))
                .build();
    }

    public SendAmountResponse sendAmount(ByteString sourceKeyString, ByteString destinationKeyString, int amount,
                                            long nonce, long timestamp, ByteString signature) {

        PublicKey sourceKey = crypto.bytesToKey(sourceKeyString);
        PublicKey destinationKey = crypto.bytesToKey(destinationKeyString);

        if (sourceKey.equals(destinationKey)) {
            throwError(SAME_ACCOUNT, nonce + 1);
        } else if (!(users.containsKey(sourceKey) && users.containsKey(destinationKey))) {
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);
        }

        String message = sourceKey + destinationKey.toString() + amount + nonce + timestamp;

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (!crypto.validateMessage(sourceKey, message, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);


        runAdeb(sig);   // ADEB!!!!


        User sourceUser = users.get(sourceKey);

        if(!validateUserNonce(sourceUser, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        if (sourceUser.getBalance() + sourceUser.getPendentAmount() < amount)
            throwError(NOT_ENOUGH_BALANCE, nonce + 1);

        addPendingAmount(-amount, sourceKey);
        addPendingTransfer(amount, sourceKey, destinationKey);  // add to dest pending list

        stateManager.saveState(users);

        return SendAmountResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(sourceKey.getEncoded()))
                .setNonce(nonce + 1)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, String.valueOf(sourceKey) + (nonce + 1))))
                .build();
    }

    public CheckAccountResponse checkAccount(ByteString checkKey, long nonce, long timestamp) {
        PublicKey pubKeyBytes = crypto.bytesToKey(checkKey);

        if (!(users.containsKey(pubKeyBytes)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        User user = users.get(pubKeyBytes);

        if(!validateUserNonce(user, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        String pendingTransfersAsString = null;
        LinkedList<Transfer> pendingTransfers = users.get(pubKeyBytes).getPendingTransfers();

        if (pendingTransfers != null)
            pendingTransfersAsString = getTransfersAsString(pendingTransfers);

        int currBalance = users.get(pubKeyBytes).getBalance();
        int pendAmount = -users.get(pubKeyBytes).getPendentAmount();
        String message = String.valueOf(currBalance) + pendAmount + pendingTransfersAsString + (nonce + 1);

        return CheckAccountResponse.newBuilder()
                .setBalance(currBalance)
                .setPendentAmount(pendAmount)
                .setPendentTransfers(pendingTransfersAsString)
                .setNonce(nonce + 1)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, message)))
                .build();
    }

    public ReceiveAmountResponse receiveAmount(ByteString pubKeyString, ByteString signature, long nonce, long timestamp) {

        PublicKey pubKey = crypto.bytesToKey(pubKeyString);

        if (!(users.containsKey(pubKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        String message = pubKey.toString() + nonce + timestamp;

        byte[] sig = crypto.byteStringToByteArray(signature);

        if (!crypto.validateMessage(pubKey, message, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);


        runAdeb(sig);   // ADEB!!!!


        User user = users.get(pubKey);

        if(!validateUserNonce(user, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        LinkedList<Transfer> pendingTransfers = user.getPendingTransfers();
        int oldBalance = user.getBalance();

        pendingTransfers.forEach(transfer -> {
            transferAmount(transfer.getDestination(), pubKey, -transfer.getAmount()); // take from senders
            transferAmount(pubKey, transfer.getDestination(), transfer.getAmount()); // transfer to receiver
        });

        pendingTransfers.clear();
        user.setPendingTransfers(pendingTransfers); // clear the list
        users.put(pubKey, user); // update user

        stateManager.saveState(users);

        int recvAmount = user.getBalance() - oldBalance;
        String newMessage = recvAmount + String.valueOf(pubKey) + (nonce + 1);

        return ReceiveAmountResponse.newBuilder()
                .setRecvAmount(recvAmount)
                .setPublicKey(ByteString.copyFrom(pubKey.getEncoded()))
                .setNonce(nonce + 1)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, newMessage)))
                .build();
    }

    public AuditResponse audit(ByteString checkKeyString, long nonce, long timestamp)  {
        PublicKey pubKey = crypto.bytesToKey(checkKeyString);

        if (!(users.containsKey(pubKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        User user = users.get(pubKey);

        if(!validateUserNonce(user, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        LinkedList<Transfer> totalTransfers = users.get(pubKey).getTotalTransfers();
        String transfers = totalTransfers.isEmpty() ? "No transfers waiting to be accepted" : getTransfersAsString(totalTransfers);

        return AuditResponse.newBuilder()
                .setTransferHistory(transfers)
                .setNonce(nonce + 1)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, transfers + (nonce + 1))))
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

        if (Arrays.equals(inputByte, this.input)) {

            System.out.println("The echo input from server " + sName + " is the same as mine.");

            this.echos.add(inputByte);
        }

        PublicKey sKey = crypto.getPublicKey(this.sName);

        if (this.echos.size() == this.byzantineEchoQuorum && !this.sentReady) {

            System.out.println("\nSending readys from echo...");

            sendReadys(input, sKey);
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

        if (Arrays.equals(inputByte, this.input)) {

            System.out.println("The ready input from server " + sName + " is the same as mine.");

            this.readys.add(inputByte);
        }

        PublicKey sKey = crypto.getPublicKey(this.sName);

        if (this.readys.size() > this.nByzantineServers && !this.sentReady) {

            System.out.println("Sending readys...");

            sendReadys(input, sKey);

        } else if (this.readys.size() == this.byzantineReadyQuorum && this.sentReady && !this.delivered) {

            this.delivered = true;
            this.latch.countDown();

        }

    }

    private void sendReadys(ByteString input, PublicKey sKey) {
        this.sentReady = true;

        long readyNonce = crypto.generateNonce();
        long readyTs = crypto.generateTimestamp();
        byte[] inputBytes = crypto.byteStringToByteArray(input);

        String message = sKey.toString() + this.sName + Arrays.toString(inputBytes) + readyNonce + readyTs;

        byte[] readySignature = crypto.encrypt(this.sName, message);

        adebFrontend.ready(
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


    private void runAdeb(byte[] clientInput) {

        if (!this.sentEcho) {

            System.out.println("Running ADEB...\n");
            this.latch = new CountDownLatch(1);

            this.input = clientInput;
            this.sentEcho = true;

            // echo
            PublicKey pubKey = crypto.getPublicKey(this.sName);
            long echoNonce = crypto.generateNonce();
            long ts = crypto.generateTimestamp();

            String message = pubKey.toString() + this.sName + Arrays.toString(clientInput) + echoNonce + ts;

            byte[] signature = crypto.encrypt(this.sName, message);

            adebFrontend.echo(
                    EchoRequest.newBuilder()
                        .setInput(ByteString.copyFrom(clientInput))
                        .setNonce(echoNonce)
                        .setTimestamp(ts)
                        .setSname(this.sName)
                        .setKey(ByteString.copyFrom(pubKey.getEncoded()))
                        .setSignature(ByteString.copyFrom(signature)).build()
            );

            await(latch);
            System.out.println("ADEB ENDED!! All servers synchronized\n\n");

            resetAdebParameters();
        }

    }

    // ------------------------------------ AUX -------------------------------------

    private void transferAmount(PublicKey senderKey, PublicKey receiverKey, int amount) {
        User user = users.get(senderKey);
        LinkedList<Transfer> totalTransfers = user.getTotalTransfers();
        totalTransfers.add(new Transfer(receiverKey, amount, false));
        user.setTotalTransfers(totalTransfers);
        user.setBalance(user.getBalance() + amount);
        if (amount < 0) user.setPendentAmount(user.getPendentAmount() - amount);
        users.put(senderKey, user);
    }

    private void addPendingTransfer(int amount, PublicKey sourceKey, PublicKey destinationKey) {
        User user = users.get(destinationKey);
        LinkedList<Transfer> destPendingTransfers = user.getPendingTransfers();
        destPendingTransfers.add(new Transfer(sourceKey, amount, true)); // added to the dest pending transfers list
        user.setPendingTransfers(destPendingTransfers);
        users.put(destinationKey, user);
    }

    private void addPendingAmount(int amount, PublicKey key) {
        User user = users.get(key);
        user.setPendentAmount(user.getPendentAmount() + amount);
        users.put(key, user);
    }

    private String getTransfersAsString(LinkedList<Transfer> pendingTransfers) {
        return pendingTransfers.stream().map(Transfer::toString).collect(Collectors.joining());
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

    private void resetAdebParameters() {
        this.input = null;
        this.latch = null;
        this.sentReady = false;
        this.sentEcho = false;
        this.delivered = false;
        this.echos.clear();
        this.readys.clear();
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
