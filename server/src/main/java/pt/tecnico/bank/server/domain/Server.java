package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import org.bouncycastle.operator.OperatorCreationException;
import pt.tecnico.bank.crypto.Crypto;
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

    public synchronized String[] openAccount(ByteString pubKey, ByteString signature)
            throws AccountAlreadyExistsException, IOException, NoSuchAlgorithmException, InvalidKeySpecException,
            UnrecoverableKeyException, CertificateException, KeyStoreException, SignatureException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, SignatureNotValidException {
        PublicKey pubKeyBytes = crypto.keyToBytes(pubKey);
        byte[] sig = new byte[256];
        signature.copyTo(sig, 0);

        if (users.containsKey(pubKeyBytes))
            throw new AccountAlreadyExistsException();

        String message = pubKeyBytes.toString();

        if (!crypto.validateMessage(pubKeyBytes, message, sig))
            throw new SignatureNotValidException();

        User newUser = new User(pubKeyBytes, 100);
        users.put(pubKeyBytes, newUser);

        String newMessage = "true" + pubKeyBytes;

        stateManager.saveState(users);
        return new String[]{"true", pubKeyBytes.toString(),
                new String(crypto.encrypt(this.sName, newMessage), StandardCharsets.ISO_8859_1)};
    }

    public synchronized String[] sendAmount(ByteString sourceKeyString, ByteString destinationKeyString, int amount,
                                            long nonce, long timestamp, ByteString signature)
            throws AccountDoesNotExistsException, SameAccountException, NotEnoughBalanceException,
            NonceAlreadyUsedException, TimestampExpiredException, SignatureNotValidException, NoSuchAlgorithmException,
            InvalidKeySpecException, UnrecoverableKeyException, CertificateException, KeyStoreException, IOException,
            SignatureException, InvalidKeyException {

        PublicKey sourceKey = crypto.keyToBytes(sourceKeyString);
        PublicKey destinationKey = crypto.keyToBytes(destinationKeyString);

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
        String[] newMessage = new String[2];
        newMessage[0] = "true";
        newMessage[1] = sourceKey.toString();
        return createResponse(newMessage, nonce, timestamp);
    }

    public synchronized String[] checkAccount(ByteString checkKey) throws AccountDoesNotExistsException,
            NoSuchAlgorithmException, InvalidKeySpecException, UnrecoverableKeyException, CertificateException,
            KeyStoreException, IOException, SignatureException, InvalidKeyException {
        PublicKey pubKeyBytes = crypto.keyToBytes(checkKey);

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
                new String(crypto.encrypt(this.sName, message), StandardCharsets.ISO_8859_1)
        };
    }

    public synchronized String[] receiveAmount(ByteString pubKeyString, ByteString signature, long nonce, long timestamp)
            throws AccountDoesNotExistsException, NonceAlreadyUsedException, TimestampExpiredException,
            SignatureNotValidException, NoSuchAlgorithmException, InvalidKeySpecException, UnrecoverableKeyException,
            CertificateException, KeyStoreException, IOException, SignatureException, InvalidKeyException {
        PublicKey pubKey = crypto.keyToBytes(pubKeyString);

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

        String[] newMessage = new String[2];
        newMessage[0] = String.valueOf(user.getBalance() - oldBalance);
        newMessage[1] = pubKey.toString();

        stateManager.saveState(users);
        return createResponse(newMessage, nonce, timestamp);
    }

    public synchronized String[] audit(ByteString checkKeyString) throws AccountDoesNotExistsException,
            NoSuchAlgorithmException, InvalidKeySpecException, UnrecoverableKeyException, CertificateException,
            KeyStoreException, IOException, SignatureException, InvalidKeyException {
        PublicKey pubKey = crypto.keyToBytes(checkKeyString);

        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        LinkedList<Transfer> totalTransfers = users.get(pubKey).getTotalTransfers();
        String message;

        long newTimestamp = System.currentTimeMillis() / 1000;
        if (totalTransfers.isEmpty()) {
            message = "No transfers waiting to be accepted" + newTimestamp;
            return new String[]{"No transfers waiting to be accepted", String.valueOf(newTimestamp),
                    new String(crypto.encrypt(this.sName, message), StandardCharsets.ISO_8859_1)};
        }
        message = getTransfersAsString(totalTransfers) + newTimestamp;
        return new String[]{getTransfersAsString(totalTransfers), String.valueOf(newTimestamp),
                new String(crypto.encrypt(this.sName, message), StandardCharsets.ISO_8859_1)};
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

    String[] createResponse(String[] message, long nonce, long timestamp) throws UnrecoverableKeyException,
            CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
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
        try {
            this.crypto.getKey(this.sName);
        } catch (NoSuchAlgorithmException | OperatorCreationException | CertificateException | IOException | KeyStoreException e) {
            e.printStackTrace();
        }
    }
}
