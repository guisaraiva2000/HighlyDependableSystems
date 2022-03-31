package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;

import pt.tecnico.bank.server.domain.exceptions.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * Facade class.
 * Contains the service operations.
 */
public class Server implements Serializable {

    private final String SERVER_PATH = System.getProperty("user.dir") + "\\KEYS\\";
    private final Path DATA_PATH = Paths.get(System.getProperty("user.dir"), "storage", "data.txt");
    private final String SERVER_PASS = "server";
    private final int OPEN_AMOUNT = 100;

    private LinkedHashMap<PublicKey, User> users = new LinkedHashMap<>();

    public Server() {
    }

    public synchronized String[] openAccount(ByteString pubKey, ByteString signature)
            throws AccountAlreadyExistsException, IOException, NoSuchAlgorithmException, InvalidKeySpecException,
            UnrecoverableKeyException, CertificateException, KeyStoreException, SignatureException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchPaddingException, SignatureNotValidException {
        PublicKey pubKeyBytes = keyToBytes(pubKey);
        byte[] sig = new byte[256];
        signature.copyTo(sig,0);

        if (users.containsKey(pubKeyBytes))
            throw new AccountAlreadyExistsException();

        String message = pubKeyBytes.toString();

        if(!validateMessage(pubKeyBytes, message, sig)){
            throw new SignatureNotValidException();
        }
        
        User newUser = new User(pubKeyBytes, OPEN_AMOUNT);
        users.put(pubKeyBytes, newUser);

        String newMessage = "true" + pubKeyBytes.toString();

        saveState();
        return new String[]{"true", pubKeyBytes.toString() , new String(encrypt(newMessage), StandardCharsets.ISO_8859_1)};
    }

    public synchronized String[] sendAmount(ByteString sourceKeyString, ByteString destinationKeyString, int amount,
                                            long nonce, long timestamp, ByteString signature)
            throws AccountDoesNotExistsException, SameAccountException, NotEnoughBalanceException,
            NonceAlreadyUsedException, TimestampExpiredException, SignatureNotValidException, NoSuchAlgorithmException,
            InvalidKeySpecException, UnrecoverableKeyException, CertificateException, KeyStoreException, IOException,
            SignatureException, InvalidKeyException {

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

        if (!validateMessage(sourceKey, message, signatureBytes))
            throw new SignatureNotValidException();

        validateNonce(sourceKey, nonce, timestamp);

        User sourceUser = users.get(sourceKey);

        if (sourceUser.getBalance() + sourceUser.getPendentAmount() < amount)
            throw new NotEnoughBalanceException();

        addPendingAmount(-amount, sourceKey);
        addPendingTransfer(amount, sourceKey, destinationKey);  // add to dest pending list

        saveState();
        String[] newMessage = new String[2];
        newMessage[0] = "true";
        newMessage[1] = sourceKey.toString();
        return createResponse(newMessage, nonce, timestamp);
    }

    public synchronized String[] checkAccount(ByteString checkKey) throws AccountDoesNotExistsException,
            NoSuchAlgorithmException, InvalidKeySpecException, UnrecoverableKeyException, CertificateException, KeyStoreException, IOException, SignatureException, InvalidKeyException {
        PublicKey pubKeyBytes = keyToBytes(checkKey);

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

        return new String[]{ String.valueOf(users.get(pubKeyBytes).getBalance()),
                String.valueOf(-users.get(pubKeyBytes).getPendentAmount()),
                pendingTransfersAsString , String.valueOf(newTimestamp),
                new String(encrypt(message), StandardCharsets.ISO_8859_1)
        };
    }

    public synchronized String[] receiveAmount(ByteString pubKeyString, ByteString signature, long nonce, long timestamp)
            throws AccountDoesNotExistsException, NonceAlreadyUsedException, TimestampExpiredException,
            SignatureNotValidException, NoSuchAlgorithmException, InvalidKeySpecException, UnrecoverableKeyException,
            CertificateException, KeyStoreException, IOException, SignatureException, InvalidKeyException {
        PublicKey pubKey = keyToBytes(pubKeyString);

        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        String message = pubKey.toString() + nonce + timestamp;

        byte[] signatureBytes = new byte[256];
        signature.copyTo(signatureBytes, 0);

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

        String[] newMessage = new String[2];
        newMessage[0] = String.valueOf(user.getBalance() - oldBalance);
        newMessage[1] = pubKey.toString();

        saveState();
        return createResponse(newMessage, nonce, timestamp);
    }

    public synchronized String[] audit(ByteString checkKeyString) throws AccountDoesNotExistsException,
            NoSuchAlgorithmException, InvalidKeySpecException, UnrecoverableKeyException, CertificateException, KeyStoreException, IOException, SignatureException, InvalidKeyException {
        PublicKey pubKey = keyToBytes(checkKeyString);

        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        LinkedList<Transfer> totalTransfers = users.get(pubKey).getTotalTransfers();
        String message  = "";

        long newTimestamp = System.currentTimeMillis() / 1000;
        if (totalTransfers.isEmpty()){
            message = "No transfers waiting to be accepted" + newTimestamp;
            return new String[]{"No transfers waiting to be accepted", String.valueOf(newTimestamp), 
                        new String(encrypt(message), StandardCharsets.ISO_8859_1)};
        }
        message = getTransfersAsString(totalTransfers) + newTimestamp;
        return new String[]{getTransfersAsString(totalTransfers), String.valueOf(newTimestamp),
                new String(encrypt(message), StandardCharsets.ISO_8859_1)};
    }

    // ------------------------------------ AUX -------------------------------------

    private void transferAmount(PublicKey senderKey, PublicKey receiverKey, int amount) {
        User user = users.get(senderKey);
        LinkedList<Transfer> totalTransfers = user.getTotalTransfers();
        totalTransfers.add(new Transfer(receiverKey, amount, false));
        user.setTotalTransfers(totalTransfers);
        user.setBalance(user.getBalance() + amount);
        if (amount < 0)  user.setPendentAmount(user.getPendentAmount() - amount);
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

    private PublicKey keyToBytes(ByteString pubKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] pubKeyBytes = new byte[294];
        pubKey.copyTo(pubKeyBytes, 0);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubKeyBytes));
    }

    private void validateNonce(PublicKey publicKey, long nonce, long timestamp)
            throws NonceAlreadyUsedException, TimestampExpiredException {
        users.get(publicKey).getNonceManager().validateNonce(nonce, timestamp);
    }

    private boolean validateMessage(PublicKey key, String message, byte[] signature) throws NoSuchAlgorithmException,
        InvalidKeyException, SignatureException {

        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initVerify(key);
        sign.update(message.getBytes(StandardCharsets.ISO_8859_1));
        return sign.verify(signature);
    }

    private byte[] encrypt(String message) throws KeyStoreException, IOException, UnrecoverableKeyException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, SignatureException {
        KeyStore ks = KeyStore.getInstance("JCEKS");
        ks.load(new FileInputStream(SERVER_PATH + SERVER_PASS + ".jks"), SERVER_PASS.toCharArray());

        PrivateKey privKey = (PrivateKey) ks.getKey(SERVER_PASS, SERVER_PASS.toCharArray());

        // SIGNATURE
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(privKey);

        sign.update(message.getBytes(StandardCharsets.ISO_8859_1));
        return sign.sign();
    }

    private String[] createResponse(String[] message, long nonce, long timestamp) throws UnrecoverableKeyException,
            CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        long newTimestamp = System.currentTimeMillis() / 1000;
        
        String[] response = new String[message.length+4];
        StringBuilder m = new StringBuilder();

        int i;
        for(i = 0; i < message.length; i++){
            response[i] = message[i];
            m.append(message[i]);
        }

        m.append(nonce + 1).append(timestamp).append(newTimestamp);
        byte[] signServer = encrypt(m.toString());
        
        response[i] = String.valueOf(nonce + 1);
        response[i+1] = String.valueOf(timestamp);
        response[i+2] = String.valueOf(newTimestamp);
        response[i+3] = new String(signServer, StandardCharsets.ISO_8859_1);

        return response;
    }

    public void loadState() {
        try (FileInputStream fis = new FileInputStream(DATA_PATH.toString()); ObjectInputStream ois = new ObjectInputStream(fis)) {
            users = (LinkedHashMap<PublicKey, User>) ois.readObject();
        } catch (FileNotFoundException fnfe) {
            try {
                Files.createDirectories(DATA_PATH.getParent());
                Files.createFile(DATA_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (EOFException e) {
            System.out.println("Warning: Database is empty");
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void saveState() throws IOException {
        byte[] userBytes = mapToBytes();

        Path tmpPath = Paths.get(System.getProperty("user.dir"), "storage");
        Path tmpPathFile = File.createTempFile("atomic", "tmp", new File(tmpPath.toString())).toPath();
        Files.write(tmpPathFile, userBytes, StandardOpenOption.APPEND);

        Files.move(tmpPathFile, DATA_PATH, StandardCopyOption.ATOMIC_MOVE);
    }

    private byte[] mapToBytes() throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(byteOut);
        out.writeObject(users);
        byte[] userBytes = byteOut.toByteArray();
        out.flush();
        byteOut.close();
        return userBytes;
    }

}
