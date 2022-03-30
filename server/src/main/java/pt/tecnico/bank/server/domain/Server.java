package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;

import pt.tecnico.bank.server.domain.exception.*;

import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.*;
import com.google.gson.*;

/**
 * Facade class.
 * Contains the service operations.
 */
public class Server {

    private final String SERVER_PATH = System.getProperty("user.dir") + "\\KEYS\\";
    private final String DATA_PATH = System.getProperty("user.dir") + "\\data.txt";
    private final String SERVER_PASS = "server";
    private Gson gson = new Gson();

    private LinkedHashMap<PublicKey, User> users = new LinkedHashMap<>();

    public Server() {

       /* ObjectInputStream ois;
        try {
            FileInputStream fis = new FileInputStream(DATA_PATH);
            ois = new ObjectInputStream(fis);
            users = (LinkedHashMap<PublicKey, User>) ois.readObject();
            fis.close();
            ois.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }



            System.out.println(users);*/
    }

    public synchronized String[] openAccount(ByteString pubKey, int balance) throws AccountAlreadyExistsException, FileNotFoundException, IOException {
        PublicKey pubKeyBytes = keyToBytes(pubKey);

        if (users.containsKey(pubKeyBytes))
            throw new AccountAlreadyExistsException();

        User newUser = new User(pubKeyBytes, balance);
        users.put(pubKeyBytes, newUser);

        /*ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_PATH));
        oos.writeObject(users);
        oos.close();*/

        String message = "true" + pubKeyBytes.toString();

        return new String[]{"true", pubKeyBytes.toString() , new String(encrypt(message), StandardCharsets.ISO_8859_1)};
    }

    //TODO fix negative balance
    public synchronized String[] sendAmount(ByteString sourceKeyString, ByteString destinationKeyString, int amount, long nonce, long timestamp, ByteString signature)
            throws AccountDoesNotExistsException, SameAccountException, NotEnoughBalanceException, NonceAlreadyUsedException, TimestampExpiredException, SignatureNotValidException {
        PublicKey sourceKey = keyToBytes(sourceKeyString);
        PublicKey destinationKey = keyToBytes(destinationKeyString);

        if (sourceKey.equals(destinationKey)) {
            throw new SameAccountException();
        } else if (!(users.containsKey(sourceKey) && users.containsKey(destinationKey))) {
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

        if (sourceUser.getBalance() + sourceUser.getPendentAmount() < amount)
            throw new NotEnoughBalanceException();

        addPendingAmount(-amount, sourceKey);
        addPendingTransfer(amount, sourceKey, destinationKey);  // add to dest pending list

        return createResponse("true", nonce, timestamp);
    }

    private void addPendingAmount(int amount, PublicKey key) {
        User user = users.get(key);
        user.setPendingAmount(user.getPendentAmount() + amount);
        users.put(key, user);
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

        String message = String.valueOf(users.get(pubKeyBytes).getBalance()) +
                        (-users.get(pubKeyBytes).getPendentAmount()) +
                        pendingTransfersAsString;

        return new String[]{ String.valueOf(users.get(pubKeyBytes).getBalance()),
                String.valueOf(-users.get(pubKeyBytes).getPendentAmount()),
                pendingTransfersAsString ,
                new String(encrypt(message), StandardCharsets.ISO_8859_1)
        };
    }

    public synchronized String[] receiveAmount(ByteString pubKeyString, ByteString signature, long nonce, long timestamp) throws AccountDoesNotExistsException, NonceAlreadyUsedException, TimestampExpiredException, SignatureNotValidException {
        PublicKey pubKey = keyToBytes(pubKeyString);

        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        String message = pubKey.toString() + nonce + timestamp;

        byte[] signatureBytes = new byte[256];
        signature.copyTo(signatureBytes, 0);

        // validate nonce too
        if (!validateMessage(pubKey, message, signatureBytes))
            throw new SignatureNotValidException();

        validateNonce(pubKey, nonce, timestamp);

        User user = users.get(pubKey);
        LinkedList<Transfer> pendingTransfers = user.getPendingTransfers();
        int oldBalance = user.getBalance();

        pendingTransfers.forEach(transfer -> {
            transferAmount(transfer.getDestination(), pubKey, -transfer.getAmount()); // take from senders
            transferAmount(pubKey, transfer.getDestination(), transfer.getAmount()); // transfer to receiver
        });

        pendingTransfers.clear();
        user.setPendingTransfers(pendingTransfers); // clear the list
        users.put(pubKey, user); // update user

        String transferredAmount = String.valueOf(user.getBalance() - oldBalance);
        return createResponse(transferredAmount, nonce, timestamp);
    }

    public synchronized String[] audit(ByteString pubKeyString) throws AccountDoesNotExistsException {
        PublicKey pubKey = keyToBytes(pubKeyString);

        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        LinkedList<Transfer> totalTransfers = users.get(pubKey).getTotalTransfers();

        if (totalTransfers == null)
            return new String[]{"No transfers waiting to be accepted",
                        new String(encrypt("No transfers waiting to be accepted"), StandardCharsets.ISO_8859_1)};

        return new String[]{getTransfersAsString(totalTransfers), new String(encrypt(getTransfersAsString(totalTransfers)), StandardCharsets.ISO_8859_1)};
    }

    // ------------------------------------ AUX -------------------------------------

    private void transferAmount(PublicKey senderKey, PublicKey receiverKey, int amount) {
        User user = users.get(senderKey);
        LinkedList<Transfer> totalTransfers = user.getTotalTransfers();
        totalTransfers.add(new Transfer(receiverKey, amount, false));
        user.setTotalTransfers(totalTransfers);
        user.setBalance(user.getBalance() + amount);
        if (amount < 0)  user.setPendingAmount(user.getPendentAmount() - amount);
        users.put(senderKey, user);
    }

    private void addPendingTransfer(int amount, PublicKey sourceKey, PublicKey destinationKey) {
        User user = users.get(destinationKey);
        LinkedList<Transfer> destPendingTransfers = user.getPendingTransfers();
        destPendingTransfers.add(new Transfer(sourceKey, amount, true)); // added to the dest pending transfers list
        user.setPendingTransfers(destPendingTransfers);
        users.put(destinationKey, user);
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

    private void validateNonce(PublicKey publicKey, long nonce, long timestamp)
            throws NonceAlreadyUsedException, TimestampExpiredException {
        users.get(publicKey).getNonceManager().validateNonce(nonce, timestamp);
    }

    private boolean validateMessage(PublicKey pubKey, String message, byte[] signature)
    {
        boolean verified = false;
        try{
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initVerify(pubKey);
            sign.update(message.getBytes(StandardCharsets.ISO_8859_1));
            verified = sign.verify(signature);
        } catch(Exception e){
            e.printStackTrace();
        }
        return verified;
    }

    private byte[] encrypt(String message){
        byte[] signature = null;
        try{
            KeyStore ks = KeyStore.getInstance("JCEKS");
            ks.load(new FileInputStream(SERVER_PATH + SERVER_PASS + ".jks"), SERVER_PASS.toCharArray());

            PrivateKey privKey = (PrivateKey) ks.getKey(SERVER_PASS, SERVER_PASS.toCharArray());

            // SIGNATURE
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initSign(privKey);

            sign.update(message.getBytes(StandardCharsets.ISO_8859_1));
            signature = sign.sign();

            
        } catch(Exception e){
            e.printStackTrace();
            System.out.println(("Error in encryption"));
        }
        return signature;
    }

    private String[] createResponse(String message, long nonce, long timestamp) {
        long newTimestamp = System.currentTimeMillis() / 1000;
        String m = message + String.valueOf(nonce + 1) + timestamp + newTimestamp;
        byte[] signServer = encrypt(m);
        
        return new String[]{message, String.valueOf(nonce + 1), String.valueOf(timestamp),
            String.valueOf(newTimestamp), new String(signServer, StandardCharsets.ISO_8859_1)};
    }
}
