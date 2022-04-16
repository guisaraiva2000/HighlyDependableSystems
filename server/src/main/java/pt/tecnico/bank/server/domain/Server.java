package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import io.grpc.Status;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.domain.exceptions.*;
import pt.tecnico.bank.server.domain.exceptions.ErrorMessage;
import pt.tecnico.bank.server.grpc.Server.*;
import static pt.tecnico.bank.server.domain.exceptions.ErrorMessage.*;


import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Facade class.
 * Contains the service operations.
 */
public class Server implements Serializable {

    private final ConcurrentHashMap<PublicKey, User> users;
    private final StateManager stateManager;
    private final Crypto crypto;
    private final String sName;

    public Server(String sName, int port, int nByzantineServers) {
        this.sName = sName;
        this.stateManager = new StateManager(sName);

        this.crypto = new Crypto(sName, sName, false);
        this.users = stateManager.loadState();

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
                .setAck(true)
                .setPublicKey(ByteString.copyFrom(pubKeyBytes.getEncoded()))
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, "true" + pubKeyBytes)))
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

        User sourceUser = users.get(sourceKey);

        if(!validateNonce(sourceUser, nonce, timestamp))
            throwError(INVALID_NONCE, nonce + 1);

        if (sourceUser.getBalance() + sourceUser.getPendentAmount() < amount)
            throwError(NOT_ENOUGH_BALANCE, nonce + 1);

        addPendingAmount(-amount, sourceKey);
        addPendingTransfer(amount, sourceKey, destinationKey);  // add to dest pending list

        stateManager.saveState(users);

        return SendAmountResponse.newBuilder()
                .setAck(true)
                .setPublicKey(ByteString.copyFrom(sourceKey.getEncoded()))
                .setNonce(nonce + 1)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, "true" + sourceKey + (nonce + 1))))
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
        String newMessage = String.valueOf(true) + recvAmount + pubKey + (nonce + 1);

        return ReceiveAmountResponse.newBuilder()
                .setAck(true)
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
        this.crypto.getKey(this.sName);
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
