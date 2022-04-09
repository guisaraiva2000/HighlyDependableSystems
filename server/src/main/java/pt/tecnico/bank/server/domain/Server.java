package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import pt.tecnico.bank.server.domain.exceptions.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * Facade class.
 * Contains the service operations.
 */
public class Server implements Serializable {

    private final HashMap<PublicKey, User> users;
    private final StateManager stateManager = new StateManager();
    private final SecurityHandler securityHandler = new SecurityHandler();

    public Server() {
        users = stateManager.loadState();
    }

    public synchronized String[] openAccount(ByteString pubKey, ByteString signature)
            throws AccountAlreadyExistsException, IOException, NoSuchAlgorithmException, InvalidKeySpecException,
            UnrecoverableKeyException, CertificateException, KeyStoreException, SignatureException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, SignatureNotValidException {
        PublicKey pubKeyBytes = securityHandler.keyToBytes(pubKey);
        byte[] sig = new byte[256];
        signature.copyTo(sig, 0);

        if (users.containsKey(pubKeyBytes))
            throw new AccountAlreadyExistsException();

        String message = pubKeyBytes.toString();

        if (!securityHandler.validateMessage(pubKeyBytes, message, sig))
            throw new SignatureNotValidException();

        User newUser = new User(pubKeyBytes, 100);
        users.put(pubKeyBytes, newUser);

        String newMessage = "true" + pubKeyBytes;

        stateManager.saveState(users);
        return new String[]{"true", pubKeyBytes.toString(),
                new String(securityHandler.encrypt(newMessage), StandardCharsets.ISO_8859_1)};
    }

    public synchronized String[] sendAmount(ByteString sourceKeyString, ByteString destinationKeyString, int amount,
                                            long nonce, long timestamp, ByteString signature)
            throws AccountDoesNotExistsException, SameAccountException, NotEnoughBalanceException,
            NonceAlreadyUsedException, TimestampExpiredException, SignatureNotValidException, NoSuchAlgorithmException,
            InvalidKeySpecException, UnrecoverableKeyException, CertificateException, KeyStoreException, IOException,
            SignatureException, InvalidKeyException {

        PublicKey sourceKey = securityHandler.keyToBytes(sourceKeyString);
        PublicKey destinationKey = securityHandler.keyToBytes(destinationKeyString);

        if (sourceKey.equals(destinationKey)) {
            throw new SameAccountException();
        } else if (!(users.containsKey(sourceKey) && users.containsKey(destinationKey))) {
            throw new AccountDoesNotExistsException();
        }

        String message = sourceKey + destinationKey.toString() + amount + nonce + timestamp;

        byte[] signatureBytes = new byte[256];
        signature.copyTo(signatureBytes, 0);

        if (!securityHandler.validateMessage(sourceKey, message, signatureBytes))
            throw new SignatureNotValidException();

        User sourceUser = users.get(sourceKey);
        securityHandler.validateNonce(sourceUser, nonce, timestamp);

        if (sourceUser.getBalance() + sourceUser.getPendentAmount() < amount)
            throw new NotEnoughBalanceException();

        addPendingAmount(-amount, sourceKey);
        addPendingTransfer(amount, sourceKey, destinationKey);  // add to dest pending list

        stateManager.saveState(users);
        String[] newMessage = new String[2];
        newMessage[0] = "true";
        newMessage[1] = sourceKey.toString();
        return securityHandler.createResponse(newMessage, nonce, timestamp);
    }

    public synchronized String[] checkAccount(ByteString checkKey) throws AccountDoesNotExistsException,
            NoSuchAlgorithmException, InvalidKeySpecException, UnrecoverableKeyException, CertificateException, KeyStoreException, IOException, SignatureException, InvalidKeyException {
        PublicKey pubKeyBytes = securityHandler.keyToBytes(checkKey);

        if (!(users.containsKey(pubKeyBytes)))
            throw new AccountDoesNotExistsException();

        String pendingTransfersAsString = null;
        LinkedList<Transfer> pendingTransfers = users.get(pubKeyBytes).getPendingTransfers();

        if (pendingTransfers != null) {
            pendingTransfersAsString = getTransfersAsString(pendingTransfers);
        }

        long newTimestamp = System.currentTimeMillis() / 1000;

        String message = String.valueOf(users.get(pubKeyBytes).getBalance()) +
                (-users.get(pubKeyBytes).getPendentAmount()) +
                pendingTransfersAsString + newTimestamp;

        return new String[]{String.valueOf(users.get(pubKeyBytes).getBalance()),
                String.valueOf(-users.get(pubKeyBytes).getPendentAmount()),
                pendingTransfersAsString, String.valueOf(newTimestamp),
                new String(securityHandler.encrypt(message), StandardCharsets.ISO_8859_1)
        };
    }

    public synchronized String[] receiveAmount(ByteString pubKeyString, ByteString signature, long nonce, long timestamp)
            throws AccountDoesNotExistsException, NonceAlreadyUsedException, TimestampExpiredException,
            SignatureNotValidException, NoSuchAlgorithmException, InvalidKeySpecException, UnrecoverableKeyException,
            CertificateException, KeyStoreException, IOException, SignatureException, InvalidKeyException {
        PublicKey pubKey = securityHandler.keyToBytes(pubKeyString);

        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        String message = pubKey.toString() + nonce + timestamp;

        byte[] signatureBytes = new byte[256];
        signature.copyTo(signatureBytes, 0);

        if (!securityHandler.validateMessage(pubKey, message, signatureBytes))
            throw new SignatureNotValidException();

        User user = users.get(pubKey);
        securityHandler.validateNonce(user, nonce, timestamp);

        LinkedList<Transfer> pendingTransfers = user.getPendingTransfers();
        int oldBalance = user.getBalance();

        pendingTransfers.forEach(transfer -> {
            transferAmount(transfer.getDestination(), pubKey, -transfer.getAmount()); // take from senders
            transferAmount(pubKey, transfer.getDestination(), transfer.getAmount()); // transfer to receiver
        });

        pendingTransfers.clear();
        user.setPendingTransfers(pendingTransfers); // clear the list
        users.put(pubKey, user); // update user

        String[] newMessage = new String[2];
        newMessage[0] = String.valueOf(user.getBalance() - oldBalance);
        newMessage[1] = pubKey.toString();

        stateManager.saveState(users);
        return securityHandler.createResponse(newMessage, nonce, timestamp);
    }

    public synchronized String[] audit(ByteString checkKeyString) throws AccountDoesNotExistsException,
            NoSuchAlgorithmException, InvalidKeySpecException, UnrecoverableKeyException, CertificateException,
            KeyStoreException, IOException, SignatureException, InvalidKeyException {
        PublicKey pubKey = securityHandler.keyToBytes(checkKeyString);

        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        LinkedList<Transfer> totalTransfers = users.get(pubKey).getTotalTransfers();
        String message = "";

        long newTimestamp = System.currentTimeMillis() / 1000;
        if (totalTransfers.isEmpty()) {
            message = "No transfers waiting to be accepted" + newTimestamp;
            return new String[]{"No transfers waiting to be accepted", String.valueOf(newTimestamp),
                    new String(securityHandler.encrypt(message), StandardCharsets.ISO_8859_1)};
        }
        message = getTransfersAsString(totalTransfers) + newTimestamp;
        return new String[]{getTransfersAsString(totalTransfers), String.valueOf(newTimestamp),
                new String(securityHandler.encrypt(message), StandardCharsets.ISO_8859_1)};
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

}
