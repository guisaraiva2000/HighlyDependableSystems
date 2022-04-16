package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.domain.exceptions.*;
import pt.tecnico.bank.server.grpc.Server.*;

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

    public synchronized OpenAccountResponse openAccount(ByteString pubKey, ByteString signature)
            throws AccountAlreadyExistsException, SignatureNotValidException {

        PublicKey pubKeyBytes = crypto.bytesToKey(pubKey);

        byte[] sig = new byte[256];
        signature.copyTo(sig, 0);

        if (users.containsKey(pubKeyBytes))
            throw new AccountAlreadyExistsException();

        String message = pubKeyBytes.toString();

        if (!crypto.validateMessage(pubKeyBytes, message, sig))
            throw new SignatureNotValidException();

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
                                            long nonce, long timestamp, ByteString signature)
            throws AccountDoesNotExistsException, SameAccountException, NotEnoughBalanceException,
            NonceAlreadyUsedException, TimestampExpiredException, SignatureNotValidException {

        PublicKey sourceKey = crypto.bytesToKey(sourceKeyString);
        PublicKey destinationKey = crypto.bytesToKey(destinationKeyString);

        if (sourceKey.equals(destinationKey)) {
            throw new SameAccountException();
        } else if (!(users.containsKey(sourceKey) && users.containsKey(destinationKey))) {
            throw new AccountDoesNotExistsException();
        }

        String message = sourceKey + destinationKey.toString() + amount + nonce + timestamp;

        byte[] signatureBytes = new byte[256];
        signature.copyTo(signatureBytes, 0);

        if (!crypto.validateMessage(sourceKey, message, signatureBytes))
            throw new SignatureNotValidException();

        User sourceUser = users.get(sourceKey);
        validateNonce(sourceUser, nonce, timestamp);

        if (sourceUser.getBalance() + sourceUser.getPendentAmount() < amount)
            throw new NotEnoughBalanceException();

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

    public synchronized CheckAccountResponse checkAccount(ByteString checkKey) throws AccountDoesNotExistsException {
        PublicKey pubKeyBytes = crypto.bytesToKey(checkKey);

        if (!(users.containsKey(pubKeyBytes)))
            throw new AccountDoesNotExistsException();

        String pendingTransfersAsString = null;
        LinkedList<Transfer> pendingTransfers = users.get(pubKeyBytes).getPendingTransfers();

        if (pendingTransfers != null)
            pendingTransfersAsString = getTransfersAsString(pendingTransfers);

        int currBalance = users.get(pubKeyBytes).getBalance();
        int pendAmount = -users.get(pubKeyBytes).getPendentAmount();
        String message = String.valueOf(currBalance) + pendAmount + pendingTransfersAsString;

        return CheckAccountResponse.newBuilder()
                .setBalance(currBalance)
                .setPendentAmount(pendAmount)
                .setPendentTransfers(pendingTransfersAsString)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, message)))
                .build();
    }

    public synchronized ReceiveAmountResponse receiveAmount(ByteString pubKeyString, ByteString signature, long nonce, long timestamp)
            throws AccountDoesNotExistsException, NonceAlreadyUsedException, TimestampExpiredException, SignatureNotValidException {
        PublicKey pubKey = crypto.bytesToKey(pubKeyString);

        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        String message = pubKey.toString() + nonce + timestamp;

        byte[] signatureBytes = new byte[256];
        signature.copyTo(signatureBytes, 0);

        if (!crypto.validateMessage(pubKey, message, signatureBytes))
            throw new SignatureNotValidException();

        User user = users.get(pubKey);
        validateNonce(user, nonce, timestamp);

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

    public synchronized AuditResponse audit(ByteString checkKeyString) throws AccountDoesNotExistsException {
        PublicKey pubKey = crypto.bytesToKey(checkKeyString);

        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        LinkedList<Transfer> totalTransfers = users.get(pubKey).getTotalTransfers();
        String transfers = totalTransfers.isEmpty() ? "No transfers waiting to be accepted" : getTransfersAsString(totalTransfers);

        return AuditResponse.newBuilder()
                .setTransferHistory(transfers)
                .setSignature(ByteString.copyFrom(crypto.encrypt(this.sName, transfers)))
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

    void validateNonce(User user, long nonce, long timestamp)
            throws NonceAlreadyUsedException, TimestampExpiredException {
        user.getNonceManager().validateNonce(nonce, timestamp);
    }

    String[] createResponse(String[] message, long nonce, long timestamp)  {
        long newTimestamp = System.currentTimeMillis() / 1000;

        String[] response = new String[message.length + 4];
        StringBuilder m = new StringBuilder();

        int i;
        for (i = 0; i < message.length; i++) {
            response[i] = message[i];
            m.append(message[i]);
        }

        m.append(nonce + 1).append(timestamp).append(newTimestamp);
        byte[] signServer = crypto.encrypt(this.sName, m.toString());

        response[i] = String.valueOf(nonce + 1);
        response[i + 1] = String.valueOf(timestamp);
        response[i + 2] = String.valueOf(newTimestamp);
        response[i + 3] = new String(signServer, StandardCharsets.ISO_8859_1);

        return response;
    }

    void initServerKeys() {
        this.crypto.getKey(this.sName);
    }
}
