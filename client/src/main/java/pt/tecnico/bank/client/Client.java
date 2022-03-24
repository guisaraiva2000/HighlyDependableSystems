package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.ServerFrontend;
import pt.tecnico.bank.server.grpc.Server.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
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

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, password.toCharArray());

            /*X509Certificate[] certChain = new X509Certificate[2];
            certChain[0] = clientCert;
            certChain[1] = caCert;*/
            
            X509Certificate[] serverChain = new X509Certificate[1];
            X509V3CertificateGenerator serverCertGen = new X509V3CertificateGenerator();
            X500Principal serverSubjectName = new X500Principal("CN=OrganizationName");
            serverCertGen.setSerialNumber(new BigInteger("123456789"));
            // X509Certificate caCert=null;
            serverCertGen.setIssuerDN(somename);
            serverCertGen.setNotBefore(new Date());
            serverCertGen.setNotAfter(new Date());
            serverCertGen.setSubjectDN(somename);
            serverCertGen.setPublicKey(serverPublicKey);
            serverCertGen.setSignatureAlgorithm("MD5WithRSA");
            // certGen.addExtension(X509Extensions.AuthorityKeyIdentifier, false,new
            // AuthorityKeyIdentifierStructure(caCert));
            serverCertGen.addExtension(X509Extensions.SubjectKeyIdentifier, false,
                new SubjectKeyIdentifierStructure(serverPublicKey));
            serverChain[0] = serverCertGen.generateX509Certificate(serverPrivateKey, "BC"); // note: private key of CA



            ks.setKeyEntry("sso-signing-key", privKey, password.toCharArray(), certChain);

            try (FileOutputStream fos = new FileOutputStream(CLIENT_PATH + "\\keystore.txt")) {
                ks.store(fos, password.toCharArray());
            }

            OpenAccountRequest req = OpenAccountRequest.newBuilder()
                                                        .setPublicKey(ByteString.copyFrom(pubKey.getEncoded()))
                                                        .setBalance(amount)
                                                        .build();
            frontend.openAccount(req);
            System.out.println("Account with key " + pubKey + " created");
        } catch (StatusRuntimeException e) {
            printError(e);
        } catch (Exception e){
            e.printStackTrace();
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

    private byte[] encrypt(String message, String password){
        byte[] signature = null;
        try{
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(new FileInputStream(CLIENT_PATH + "\\keystore.txt"), password.toCharArray());
            PrivateKey privKey = (PrivateKey) ks.getKey("sso-signing-key", password.toCharArray());

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