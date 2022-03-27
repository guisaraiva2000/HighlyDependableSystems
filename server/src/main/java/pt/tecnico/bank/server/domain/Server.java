package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import pt.tecnico.bank.server.domain.exception.*;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.stream.Collectors;

import java.nio.charset.StandardCharsets;
import java.security.Signature;

/**
 * Facade class.
 * Contains the service operations.
 */
public class Server {

    private final LinkedHashMap<PublicKey, User> users = new LinkedHashMap<>();

    public Server() {}

    public synchronized boolean openAccount(ByteString pubKey, int balance) throws AccountAlreadyExistsException {
        PublicKey pubKeyBytes = keyToBytes(pubKey);

        if (users.containsKey(pubKeyBytes))
            throw new AccountAlreadyExistsException();

        User newUser = new User(pubKeyBytes, balance);
        users.put(pubKeyBytes, newUser);

        return true;
    }

    public synchronized boolean sendAmount(ByteString sourceKey, ByteString destinationKey, int amount, String nonce, long timestamp)
            throws AccountDoesNotExistsException, SameAccountException, NotEnoughBalanceException, NonceAlreadyUsedException, TimestampExpiredException {
        PublicKey sourceKeyBytes = keyToBytes(sourceKey);
        PublicKey destinationKeyBytes = keyToBytes(destinationKey);

        System.out.println(timestamp);
        System.out.println(System.currentTimeMillis());

        /*for(PublicKey k : users.keySet())
            System.out.println(k);*/

        /*if (sourceKey.equals(destinationKey)) {
            throw new SameAccountException();
        } else */if (!(users.containsKey(sourceKeyBytes) && users.containsKey(destinationKeyBytes))) {
            throw new AccountDoesNotExistsException();
        }

        /*SendMoney message = new SendMoney
        (
            sourceyKeyBytes,
            destinationKeyBytes,
            amount,
            nonce
        );*/

        //validateMessage(sourceKeyBytes, message.toString(), request.getSignature());

        validateNonce(sourceKeyBytes, nonce, timestamp);

        User sourceUser = users.get(sourceKeyBytes);

        if (sourceUser.getBalance() < amount)
            throw new NotEnoughBalanceException();

        // TODO check if it was the source user that made the transfer ?????

        User destUser = users.get(destinationKeyBytes);
        LinkedList<Transfer> destPendingTransfers = destUser.getPendingTransfers();
        destPendingTransfers.add(new Transfer(sourceKeyBytes, amount)); // added to the dest pending transfers list
        destUser.setPendingTransfers(destPendingTransfers);
        users.put(destinationKeyBytes, destUser);

        System.out.println(users.get(destinationKeyBytes).toString());

        return true;
    }

    public synchronized String[] checkAccount(ByteString pubKey) throws AccountDoesNotExistsException {
        PublicKey pubKeyBytes = keyToBytes(pubKey);

        if (!(users.containsKey(pubKeyBytes)))
            throw new AccountDoesNotExistsException();

        String pendingTransfersAsString = null;
        LinkedList<Transfer> pendingTransfers = users.get(pubKeyBytes).getPendingTransfers();

        if (pendingTransfers != null) {
            pendingTransfersAsString = getTransfersAsString(pendingTransfers);
        }

        return new String[]{ String.valueOf(users.get(pubKeyBytes).getBalance()), pendingTransfersAsString };
    }

    public synchronized boolean receiveAmount(ByteString pubKey) throws AccountDoesNotExistsException {
        PublicKey pubKeyBytes = keyToBytes(pubKey);

        if (!(users.containsKey(pubKeyBytes)))
            throw new AccountDoesNotExistsException();

        User user = users.get(pubKeyBytes);
        LinkedList<Transfer> pendingTransfers = user.getPendingTransfers();

        pendingTransfers.forEach(transfer -> {
            transferAmount(transfer.getDestination(), pubKeyBytes, -transfer.getAmount()); // take from senders
            transferAmount(pubKeyBytes, transfer.getDestination(), transfer.getAmount()); // transfer to receiver
        });

        pendingTransfers.clear();
        user.setPendingTransfers(pendingTransfers); // clear the list
        users.put(pubKeyBytes, user); // update user

        return true;
    }

    public synchronized String audit(ByteString pubKey) throws AccountDoesNotExistsException {
        PublicKey pubKeyBytes = keyToBytes(pubKey);

        if (!(users.containsKey(pubKeyBytes)))
            throw new AccountDoesNotExistsException();

        LinkedList<Transfer> totalTransfers = users.get(pubKeyBytes).getTotalTransfers();

        if (totalTransfers != null)
            return getTransfersAsString(totalTransfers);

        return "No transfers found.";
    }

    // ------------------------------------ AUX -------------------------------------

    private void transferAmount(PublicKey senderKey, PublicKey receiverKey, int amount) {
        User user = users.get(senderKey);
        LinkedList<Transfer> totalTransfers = user.getTotalTransfers();
        totalTransfers.add(new Transfer(receiverKey, amount));
        user.setTotalTransfers(totalTransfers);
        user.setBalance(user.getBalance() + amount);
        users.put(senderKey, user);
    }

    private String getTransfersAsString(LinkedList<Transfer> pendingTransfers) {
        return pendingTransfers.stream().map(Transfer::toString).collect(Collectors.joining());
    }

    private PublicKey keyToBytes(ByteString pubKey) {
        byte[] pubKeyBytes = new byte[294];
        pubKey.copyTo(pubKeyBytes, 0);

        PublicKey publicKey = null;

        try {
            publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubKeyBytes));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return publicKey;
    }

    private void validateNonce(PublicKey publicKey, String nonce, long timestamp)
            throws NonceAlreadyUsedException, TimestampExpiredException {
        users.get(publicKey).getNonceManager().validateNonce(nonce, timestamp);
        System.out.println(users.get(publicKey).getNonceManager());
    }

    private boolean validateMessage(byte[] pubKey, String message, byte[] sign)
    {
       /* try{
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(pubKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
        } catch(Exception e){
            e.printStackTrace();
        }

        return signature.verify(sign);*/
        return true;
    }
}
