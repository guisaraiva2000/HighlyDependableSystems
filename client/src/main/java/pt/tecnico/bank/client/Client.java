package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.ServerFrontend;
import pt.tecnico.bank.server.grpc.Server.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.spec.*;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.File;
import javax.crypto.*;

public class Client {

    private final ServerFrontend frontend;
    private final String KEY_STORE = "keystore";
    private final String CLIENT_PATH = System.getProperty("user.dir");

    public Client(ServerFrontend frontend){
        this.frontend = frontend;
    }

    void open_account(int amount, String password){

        try {
            KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
            rsaKeyGen.initialize(2048);

            KeyPair rsaKeyPair = rsaKeyGen.generateKeyPair();

            Key pubKey = rsaKeyPair.getPublic();
            Key privKey = rsaKeyPair.getPrivate();

            Certificate[] certChain = new Certificate[2];
            //certChain[0] = clientCert;

            KeyStore ks = KeyStore.getInstance("RSA");
            ks.setKeyEntry("client", privKey, password.toCharArray(), certChain);
            OutputStream writeStream = new FileOutputStream(CLIENT_PATH);
            ks.store(writeStream, password.toCharArray());
            writeStream.close();

            OpenAccountRequest req = OpenAccountRequest.newBuilder()
                                                        .setPublicKey(ByteString.copyFrom(pubKey.getEncoded()))
                                                        .setBalance(amount)
                                                        .build();
            frontend.openAccount(req);
            System.out.println("Account with key " + pubKey + " created");
        } catch (StatusRuntimeException e) {
            printError(e);
        } catch (Exception e){
            System.out.println("Error while openning account");
        }
    }

    void send_amount(ByteString orig_key, ByteString dest_key, int amount, String password){
        try {

            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);

            SendMoney message = new SendMoney(
                orig_key,
                dest_key,
                amount,
                nonce
            );

            // TODO
            byte[] signature = encrypt(message.toString(), password);

            // send encrypted message isntead of clear message
            SendAmountRequest req = SendAmountRequest.newBuilder().setSourceKey(orig_key)
                                                                .setDestinationKey(dest_key)
                                                                .setAmount(amount)
                                                                .setSignature(ByteString.copyFrom(signature))
                                                                .build();
            frontend.sendAmount(req);
            System.out.println("Sent " + amount + " from " + orig_key + " to " + dest_key);
        } catch (StatusRuntimeException e) {
            printError(e);
        } catch (Exception e){
            System.out.println("Error while sending amount");
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

    private byte[] encrypt(String password, String message){
        byte[] signature = null;
        try{
            // GETTING THE PRIVATE KEY
           /* File privateKeyFile = new File(CLIENT_PATH + "\\private.txt");
            byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec privateKeySpec = new X509EncodedKeySpec(privateKeyBytes);
            Key privKey = keyFactory.generatePrivate(privateKeySpec);*/

           /* KeyStore ks = KeyStore.getInstance("RSA");
            ks.load(new FileInputStream(pubKey), password.toCharArray());
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_STORE, "changeit");*/

            KeyStore ks = KeyStore.getInstance("RSA");
            InputStream readStream = new FileInputStream(CLIENT_PATH);
            ks.load(readStream, password.toCharArray());
            PrivateKey privKey = (PrivateKey) ks.getKey("client", password.toCharArray());
            readStream.close();

            // SIGNATURE
            Signature sign = Signature.getInstance("SHA256withDSA");
            sign.initSign(privKey);

            sign.update(message.getBytes("UTF-8"));
            signature = sign.sign();

            
        } catch (IOException e) {
            e.printStackTrace();
        } catch(Exception e){
            System.out.println(("Error in encryption"));
        }
        
        return signature;
    }

    private String decrypt(byte[] message) {
        String decryptedMessage = "";

        try {
            File publicKeyFile = new File(CLIENT_PATH + "\\public.txt");
            byte[] publicKeyBytes = Files.readAllBytes(publicKeyFile.toPath());

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            Key pubKey = keyFactory.generatePrivate(publicKeySpec);

            Cipher decryptCipher = Cipher.getInstance("RSA");
            decryptCipher.init(Cipher.DECRYPT_MODE, pubKey);

            byte[] decryptedMessageBytes = decryptCipher.doFinal(message);
            decryptedMessage = new String(decryptedMessageBytes, StandardCharsets.UTF_8);

        } catch(Exception e){
            System.out.println(("Error in encryption"));
        }

        return decryptedMessage;
    }

}