package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.ServerFrontend;
import pt.tecnico.bank.server.grpc.Server;
import java.security.*;
import java.security.spec.*;
import java.io.FileOutputStream;
import java.nio.file.*;
import java.io.File;
import javax.crypto.*;

public class Client {

    private final ServerFrontend frontend;

    private final String CLIENT_PATH = System.getProperty("user.dir");

    public Client(ServerFrontend frontend){
        this.frontend = frontend;
    }

    void open_account(int amount){

        KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
        rsaKeyGen.initialize(2048);

        KeyPair rsaKeyPair = rsaKeyGen.generateKeyPair();

        Key pubKey = rsaKeyPair.getPublic();
        Key privKey = rsaKeyPair.getPrivate();

        // write private key into client folder
        try (FileOutputStream fos = new FileOutputStream(CLIENT_PATH + "private.key")) {
            fos.write(pubKey.getEncoded());
        }

        // write public key into client folder
        try (FileOutputStream fos = new FileOutputStream(CLIENT_PATH + "public.key")) {
            fos.write(pubKey.getEncoded());
        }

        try {
            OpenAccountRequest req = OpenAccountRequest.newBuilder().setPublicKey(pubKey)
                                                                  .setBalance(amount).build();
            frontend.openAccount(req);
            System.out.println("Account with key " + pubKey + " created");
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    void send_amount(ByteString orig_key, ByteString dest_key, int amount){

        

        String message = "stuff to send";

        

        string secretMessage = encrypt(message);

        try {
            SendAmountRequest req = SendAmountRequest.newBuilder().setSourceKey(orig_key)
                                                                .setDestinationKey(dest_key)
                                                                .setAmount(amount).build();
            frontend.sendAmount(req);
            System.out.println("Send " + amount + " from " + orig_key + " to " + dest_key);
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    void check_account(ByteString key){
        try {
            CheckAccountRequest req = CheckAccountRequest.newBuilder().setPublicKey(key).build();
            CheckAccountResponse res = frontend.checkAccount(req);

            System.out.println("Account Status:\n" + "Balance: " + res.getBalance() +
                    "\nPending transfers: " + res.getPendentTransfers() );
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    void receive_amount(ByteString key){
        try {
            ReceiveAmountRequest req = ReceiveAmountRequest.newBuilder().setPublicKey(key).build();
            frontend.receiveAmount(req);
            System.out.println("Amount deposited to your account");
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }
    
    void audit(ByteString key){
        try {
            AuditRequest req = AuditRequest.newBuilder().setPublicKey(key).build();
            AuditResponse res = frontend.auditResponse(req);
            System.out.println("Total tranfers:\n" + res.getTransferHistory());
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    private void printError(StatusRuntimeException e) {
        if (e.getStatus().getDescription() != null && e.getStatus().getDescription().equals("io exception")) {
            System.out.println("Warn: Server not responding!");
        } else {
            System.out.println(e.getStatus().getDescription());
        }
    }

    public static byte[] encrypt(String message) {
    
        File privateKeyFile = new File(CLIENT_PATH + "private.key");
        byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec privateKeySpec = new X509EncodedKeySpec(privateKeyBytes);
        Key privKey = keyFactory.generatePrivate(privateKeySpec);

        Cipher encryptCipher = Cipher.getInstance("RSA");
        encryptCipher.init(Cipher.ENCRYPT_MODE, privKey);

        byte[] secretMessageBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedMessageBytes = encryptCipher.doFinal(secretMessageBytes);

        return encryptedMessageBytes;
    }

    public static byte[] decrypt(String message) {
        File publicKeyFile = new File(CLIENT_PATH + "public.key");
        byte[] publicKeyBytes = Files.readAllBytes(publicKeyFile.toPath());

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        Key pubKey = keyFactory.generatePrivate(publicKeySpec);

        Cipher decryptCipher = Cipher.getInstance("RSA");
        decryptCipher.init(Cipher.DECRYPT_MODE, pubKey);

        byte[] decryptedMessageBytes = decryptCipher.doFinal(decryptedMessageBytes);
        String decryptedMessage = new String(decryptedMessageBytes, StandardCharsets.UTF_8);

        return decryptedMessageBytes;
    }

}