package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.domain.adeb.AdebFrontend;
import pt.tecnico.bank.server.domain.exceptions.ErrorMessage;
import pt.tecnico.bank.server.domain.exceptions.ServerStatusRuntimeException;
import pt.tecnico.bank.server.grpc.Adeb.*;
import pt.tecnico.bank.server.grpc.Adeb.EchoResponse;
import pt.tecnico.bank.server.grpc.Adeb.ReadyResponse;
import pt.tecnico.bank.server.grpc.Server.*;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
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
    private final AdebFrontend adebFrontend;

    // ADEB
    private ByteString input;

    public ServerBackend(String sName, int nByzantineServers) {
        this.sName = sName;
        this.stateManager = new StateManager(sName);

        this.crypto = new Crypto(sName, sName, false);
        this.users = stateManager.loadState();

        this.adebFrontend = new AdebFrontend(nByzantineServers, crypto);
        this.input = null;

        initServerKeys();
    }

    public synchronized OpenAccountResponse openAccount(ByteString pubKey, ByteString signature) {

        PublicKey pubKeyBytes = crypto.bytesToKey(pubKey);

        byte[] sig = crypto.getSignature(signature);

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

    public synchronized SendAmountResponse sendAmount(ByteString sourceKeyString, ByteString destinationKeyString, int amount,
                                            long nonce, long timestamp, ByteString signature) {

        PublicKey sourceKey = crypto.bytesToKey(sourceKeyString);
        PublicKey destinationKey = crypto.bytesToKey(destinationKeyString);

        if (sourceKey.equals(destinationKey)) {
            throwError(SAME_ACCOUNT, nonce + 1);
        } else if (!(users.containsKey(sourceKey) && users.containsKey(destinationKey))) {
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);
        }

        String message = sourceKey + destinationKey.toString() + amount + nonce + timestamp;

        byte[] sig = crypto.getSignature(signature);

        if (!crypto.validateMessage(sourceKey, message, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);

        // ADEB
        performAdeb(signature);

        User sourceUser = users.get(sourceKey);

        if(!validateNonce(sourceUser, nonce, timestamp))
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

    public synchronized CheckAccountResponse checkAccount(ByteString checkKey, long nonce, long timestamp) {
        PublicKey pubKeyBytes = crypto.bytesToKey(checkKey);

        if (!(users.containsKey(pubKeyBytes)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        User user = users.get(pubKeyBytes);

        if(!validateNonce(user, nonce, timestamp))
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

    public synchronized ReceiveAmountResponse receiveAmount(ByteString pubKeyString, ByteString signature, long nonce, long timestamp) {

        PublicKey pubKey = crypto.bytesToKey(pubKeyString);

        if (!(users.containsKey(pubKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        String message = pubKey.toString() + nonce + timestamp;

        byte[] sig = crypto.getSignature(signature);

        if (!crypto.validateMessage(pubKey, message, sig))
            throwError(INVALID_SIGNATURE, nonce + 1);

        User user = users.get(pubKey);

        if(!validateNonce(user, nonce, timestamp))
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

    public synchronized AuditResponse audit(ByteString checkKeyString, long nonce, long timestamp)  {
        PublicKey pubKey = crypto.bytesToKey(checkKeyString);

        if (!(users.containsKey(pubKey)))
            throwError(ACCOUNT_DOES_NOT_EXIST, nonce + 1);

        User user = users.get(pubKey);

        if(!validateNonce(user, nonce, timestamp))
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

    // backend aka receive
    public EchoResponse echo(ByteString pubKeyString, ByteString signature, long nonce, ByteString input) {
        // verificar assinatura e nonce
        // verifico se input == meu input
        // return true
        // return echoresponse(key, nonce, true, assinatura)
        return null;
    }

    public ReadyResponse ready(ByteString pubKeyString, ByteString signature, long nonce, ByteString input) {
        // verificar assinatura e nonce
        // verifico se input == meu input
        // return true
        // return echoresponse(key, nonce, true, assinatura)
        return null;
    }

    // frontend aka send
    private void performAdeb(ByteString clientInput) {
        this.input = clientInput;

        // echo
        PublicKey pubKey = crypto.getPublicKey(this.sName);
        long echoNonce = crypto.generateNonce();

        String message = pubKey.toString() + clientInput + echoNonce;

        byte[] signature = crypto.encrypt(sName, message);

        adebFrontend.echo(EchoRequest.newBuilder()
                .setInput(clientInput)
                .setNonce(echoNonce)
                .setKey(ByteString.copyFrom(pubKey.getEncoded()))
                .setSignature(ByteString.copyFrom(signature)).build());

        // ready
        long readyNonce = crypto.generateNonce();
        message = pubKey.toString() + clientInput + readyNonce;
        signature = crypto.encrypt(sName, message);

        adebFrontend.ready(ReadyRequest.newBuilder()
                .setInput(clientInput)
                .setNonce(readyNonce)
                .setKey(ByteString.copyFrom(pubKey.getEncoded()))
                .setSignature(ByteString.copyFrom(signature)).build());
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

    private boolean validateNonce(User user, long nonce, long timestamp) {
        return user.getNonceManager().validateNonce(nonce, timestamp);
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
}
