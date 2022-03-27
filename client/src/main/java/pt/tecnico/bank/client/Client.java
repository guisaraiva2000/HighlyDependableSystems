package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.apache.commons.lang3.RandomStringUtils;
import pt.tecnico.bank.server.ServerFrontend;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.math.BigInteger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import pt.tecnico.bank.server.grpc.Server.*;

import javax.crypto.Cipher;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class Client {

    private final ServerFrontend frontend;
    private final String KEY_STORE = "keystore";
    private final String CLIENT_PATH = System.getProperty("user.dir");
    private final String CERT_PATH = System.getProperty("user.dir") + "\\CERTIFICATES\\";

    public Client(ServerFrontend frontend){
        this.frontend = frontend;
    }

    void open_account(String accountName, int amount, String password){

        try {
            KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
            rsaKeyGen.initialize(2048);

            KeyPair rsaKeyPair = rsaKeyGen.generateKeyPair();

            Key pubKey = rsaKeyPair.getPublic();
            Key privKey = rsaKeyPair.getPrivate();

            KeyStore ks = KeyStore.getInstance("JCEKS");
            ks.load(null, password.toCharArray());

           // ks.load(new FileInputStream(CLIENT_PATH + "\\keystore.jks"), password.toCharArray());
            X509Certificate[] certificateChain = new X509Certificate[1];
            certificateChain[0] = selfSign(rsaKeyPair, accountName);
 
            ks.setKeyEntry(accountName, privKey, password.toCharArray(), certificateChain);

            try (FileOutputStream fos = new FileOutputStream(CLIENT_PATH + "\\keystore.jks")) {
                ks.store(fos, password.toCharArray());
            }

            byte[] encoded = pubKey.getEncoded();
            try (FileOutputStream out = new FileOutputStream("test_client2.txt")) {
                out.write(encoded);
            }

            OpenAccountRequest req = OpenAccountRequest.newBuilder()
                                                        .setPublicKey(ByteString.copyFrom(encoded))
                                                        .setBalance(amount)
                                                        .build();

            frontend.openAccount(req);
            System.out.println("Account with key " + pubKey + " created with len: " +  pubKey.getEncoded().length);
        } catch (StatusRuntimeException e) {
            printError(e);
        } catch (Exception e){
            e.printStackTrace();
        }
    }


    void send_amount(String senderAccount, String receiverAccount, int amount, String password){
        try {

            String nonce = RandomStringUtils.randomAlphanumeric(10);
            long timestamp = System.currentTimeMillis() / 1000;

            byte[] orig_bytes = getPublicKey(senderAccount).getEncoded();
            byte[] dest_bytes = getPublicKey(receiverAccount).getEncoded();

          /*  for(int i=0; i< orig_bytes.length ; i++)
                System.out.print(orig_bytes[i] +" ");

            for(int j=0; j< dest_bytes.length ; j++)
                System.out.print(dest_bytes[j] +" ");*/

            SendMoney message = new SendMoney(
                orig_bytes,
                dest_bytes,
                amount,
                nonce
            );

            // TODO
            byte[] signature = encrypt(senderAccount, message.toString(), password);

            // send encrypted message instead of clear message
            SendAmountRequest req = SendAmountRequest.newBuilder().setSourceKey(ByteString.copyFrom(orig_bytes))
                                                                .setDestinationKey(ByteString.copyFrom(dest_bytes))
                                                                .setAmount(amount)
                                                                .setSignature(ByteString.copyFrom(signature))
                                                                .setNonce(nonce)
                                                                .setTimestamp(timestamp)
                                                                .build();
            frontend.sendAmount(req);
            System.out.println("Sent " + amount + " from " + senderAccount + " to " + receiverAccount);
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

    private byte[] encrypt(String alias, String message, String password){
        byte[] signature = null;
        try{
            KeyStore ks = KeyStore.getInstance("JCEKS");
            ks.load(new FileInputStream(CLIENT_PATH + "\\keystore.jks"), password.toCharArray());

            PrivateKey privKey = (PrivateKey) ks.getKey(alias, password.toCharArray());

            // SIGNATURE
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initSign(privKey);

            sign.update(message.getBytes(StandardCharsets.UTF_8));
            signature = sign.sign();

            
        } catch (IOException e) {
            e.printStackTrace();
        } catch(Exception e){
            e.printStackTrace();
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

    private X509Certificate selfSign(KeyPair keyPair, String subjectDN)
    {
        X509Certificate certificate = null;
        try{
            Provider bcProvider = new BouncyCastleProvider();
            Security.addProvider(bcProvider);

            long now = System.currentTimeMillis();
            Date startDate = new Date(now);

            X500Name dnName = new X500Name("CN="+subjectDN);
            BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // <-- Using the current timestamp as the certificate serial number

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(startDate);
            calendar.add(Calendar.YEAR, 1); // <-- 1 Yr validity

            Date endDate = calendar.getTime();
            String signatureAlgorithm = "SHA256WithRSA"; // <-- Use appropriate signature algorithm based on your keyPair algorithm.
            ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());
            JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());

            // Basic Constraints
            BasicConstraints basicConstraints = new BasicConstraints(true); // <-- true for CA, false for EndEntity
            certBuilder.addExtension(new ASN1ObjectIdentifier("2.5.29.19"), true, basicConstraints); // Basic Constraints is usually marked as critical.
            certificate = new JcaX509CertificateConverter().setProvider(bcProvider).getCertificate(certBuilder.build(contentSigner));

            byte[] buf = certificate.getEncoded();
    
            File file = new File(CERT_PATH + subjectDN + ".cert"); 
            file.createNewFile();
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(buf);
            }

        } catch (Exception e){
            e.printStackTrace();
        }


        return certificate;
    }

    private String createRandomNonce() 
    {
        final byte[] ar = new byte[48];
        new SecureRandom().nextBytes(ar);
        final String nonce = new String(java.util.Base64.getUrlEncoder().withoutPadding().encode(ar), StandardCharsets.UTF_8);
        Arrays.fill(ar, (byte) 0);
        return nonce;
    }

    private PublicKey getPublicKey(String alias)
    {
        X509Certificate cert = null;

        try{
            CertificateFactory fac = CertificateFactory.getInstance("X509");
            FileInputStream in = new FileInputStream(CERT_PATH + alias + ".cert");
            cert = (X509Certificate) fac.generateCertificate(in);
        } catch(Exception e){
            e.printStackTrace();
        }

        return cert.getPublicKey();

    }

}