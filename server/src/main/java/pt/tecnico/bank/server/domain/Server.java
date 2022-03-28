package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import pt.tecnico.bank.server.domain.exception.*;

import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;

/**
 * Facade class.
 * Contains the service operations.
 */
public class Server {

    private final String SERVER_PATH = System.getProperty("user.dir") + "\\KEYS\\";

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

    public synchronized String[] sendAmount(ByteString sourceKeyString, ByteString destinationKeyString, int amount, String nonce, long timestamp, ByteString signature)
            throws AccountDoesNotExistsException, SameAccountException, NotEnoughBalanceException, NonceAlreadyUsedException, TimestampExpiredException, SignatureNotValidException {
        PublicKey sourceKey = keyToBytes(sourceKeyString);
        PublicKey destinationKey = keyToBytes(destinationKeyString);

        /*if (sourceKey.equals(destinationKey)) {
            throw new SameAccountException();
        } else */if (!(users.containsKey(sourceKey) && users.containsKey(destinationKey))) {
            throw new AccountDoesNotExistsException();
        }

        String message = sourceKey.toString() + destinationKey.toString() + String.valueOf(amount) + nonce + String.valueOf(timestamp);

        byte[] signatureBytes = new byte[256];
        signature.copyTo(signatureBytes, 0);

        // validate nonce too
        if (!validateMessage(sourceKey, message, signatureBytes))
            throw new SignatureNotValidException();

        validateNonce(sourceKey, nonce, timestamp);

        User sourceUser = users.get(sourceKey);

        if (sourceUser.getBalance() < amount)
            throw new NotEnoughBalanceException();

        // TODO check if it was the source user that made the transfer ?????

        User destUser = users.get(destinationKey);
        LinkedList<Transfer> destPendingTransfers = destUser.getPendingTransfers();
        destPendingTransfers.add(new Transfer(sourceKey, amount)); // added to the dest pending transfers list
        destUser.setPendingTransfers(destPendingTransfers);
        users.put(destinationKey, destUser);

        String[] response = createResponse(nonce, timestamp);

        System.out.println("EVERYTHING OK");
        return response;
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

    public synchronized String[] receiveAmount(ByteString pubKeyString, ByteString signature, String nonce, long timestamp) throws AccountDoesNotExistsException, NonceAlreadyUsedException, TimestampExpiredException, SignatureNotValidException {
        PublicKey pubKey = keyToBytes(pubKeyString);

        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        // timestamp is 0
        String message = pubKey.toString() + nonce + String.valueOf(timestamp);
        System.out.println("MESSAGE " + message);

        byte[] signatureBytes = new byte[256];
        signature.copyTo(signatureBytes, 0);
    
        // validate nonce too
        if (!validateMessage(pubKey, message, signatureBytes))
            throw new SignatureNotValidException();
    
        validateNonce(pubKey, nonce, timestamp);

        User user = users.get(pubKey);
        LinkedList<Transfer> pendingTransfers = user.getPendingTransfers();

        pendingTransfers.forEach(transfer -> {
            transferAmount(transfer.getDestination(), pubKey, -transfer.getAmount()); // take from senders
            transferAmount(pubKey, transfer.getDestination(), transfer.getAmount()); // transfer to receiver
        });

        pendingTransfers.clear();
        user.setPendingTransfers(pendingTransfers); // clear the list
        users.put(pubKey, user); // update user

        String[] response = createResponse(nonce, timestamp);

        System.out.println("EVERYTHING OK");

        return response;
    }

    public synchronized String audit(ByteString pubKeyString) throws AccountDoesNotExistsException {
        PublicKey pubKey = keyToBytes(pubKeyString);

        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        LinkedList<Transfer> totalTransfers = users.get(pubKey).getTotalTransfers();

        if (totalTransfers == null)
            return "No transfers waiting for acceptance";
        
        
        System.out.println("EVERYTHING OK");
        return "All pending transfers moved to account";
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
        System.out.println(users.get(publicKey).getNonceManager().getNonces());
    }

    private boolean validateMessage(PublicKey pubKey, String message, byte[] signature)
    {
        boolean verified = true;
        try{
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initVerify(pubKey);
            sign.update(message.getBytes(StandardCharsets.UTF_8));
            verified = sign.verify(signature);
        } catch(Exception e){
            e.printStackTrace();
        }
        return verified;
    }

    private byte[] encrypt(String message){
        byte[] signature = null;
        try{

            byte[] key = Files.readAllBytes(Paths.get(SERVER_PATH + "private.key"));
            System.out.println(SERVER_PATH + "private.key");
            System.out.println(key.length);

            PrivateKey privKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));
            
            // SIGNATURE
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initSign(privKey);

            sign.update(message.getBytes(StandardCharsets.UTF_8));
            signature = sign.sign();

        } catch(Exception e){
            e.printStackTrace();
            System.out.println(("Error in encryption"));
        }
        
        return signature;
    }

    private String[] createResponse(String nonce, long timestamp) {
        long newTimestamp = System.currentTimeMillis() / 1000;
        String m = true + nonce + timestamp + newTimestamp;
        byte[] signServer = encrypt(m);

        return new String[]{"true", nonce, String.valueOf(timestamp),
                String.valueOf(newTimestamp), new String(signServer, StandardCharsets.UTF_8)};
    }
}
