package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import pt.tecnico.bank.server.ServerFrontend;
import pt.tecnico.bank.server.grpc.Server.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

public class Client {

    private final String username;

    private final ServerFrontend frontend;
    private final String CERT_PATH = System.getProperty("user.dir") + "\\CERTIFICATES\\";

    private final String client_path;

    public Client(ServerFrontend frontend, String username){
        this.frontend = frontend;
        this.username = username;
        client_path = System.getProperty("user.dir") + "\\CLIENTS\\" + username + "\\";
    }

    void open_account(String accountName, int amount, String password){

        try {
            KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
            rsaKeyGen.initialize(2048);

            KeyPair rsaKeyPair = rsaKeyGen.generateKeyPair();

            Key pubKey = rsaKeyPair.getPublic();
            Key privKey = rsaKeyPair.getPrivate();

            KeyStore ks = KeyStore.getInstance("JCEKS");

            File file = new File(client_path + username + ".jks");

            if(!file.exists())
                ks.load(null, password.toCharArray());
            else
                ks.load(new FileInputStream(file), password.toCharArray());

            X509Certificate[] certificateChain = new X509Certificate[1];
            certificateChain[0] = selfSign(rsaKeyPair, accountName);

            ks.setKeyEntry(accountName, privKey, password.toCharArray(), certificateChain);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                ks.store(fos, password.toCharArray());
            }

            try (FileOutputStream fos = new FileOutputStream(client_path + "public.key")) {
                fos.write(pubKey.getEncoded());
            }

            try (FileOutputStream fos = new FileOutputStream(client_path + "private.key")) {
                fos.write(privKey.getEncoded());
            }

            byte[] encoded = pubKey.getEncoded();

            OpenAccountRequest req = OpenAccountRequest.newBuilder()
                    .setPublicKey(ByteString.copyFrom(encoded))
                    .setBalance(amount)
                    .build();

            OpenAccountResponse res = frontend.openAccount(req);

            boolean ack = res.getAck();
            ByteString keyString = res.getPublicKey();
            byte[] key = new byte[698];
            keyString.copyTo(key,0);
            byte[] signature = new byte[256];
            res.getSignature().copyTo(signature,0);

            String message = ack + pubKey.toString();

            if(validateResponse(getPublicKey("server"), message, signature))
                System.out.println("Account with name " + accountName + " created");
            else
                System.out.println("WARNING! Invalid message from server. Someone might be intercepting your messages with the server.");

        } catch (StatusRuntimeException e) {
            printError(e);
        } catch (Exception e){
            e.printStackTrace();
        }
    }


    void send_amount(String senderAccount, String receiverAccount, int amount, String password){
        try {

            long nonce = new SecureRandom().nextLong(); // 96-bit recommend by NIST
            long timestamp = System.currentTimeMillis() / 1000;

            PublicKey origKey = getPublicKey(senderAccount);
            PublicKey destKey = getPublicKey(receiverAccount);

            String message = origKey.toString() + destKey.toString() + amount + nonce + timestamp;

            byte[] signature = encrypt(senderAccount, message, password);

            // send encrypted message instead of clear message
            SendAmountRequest req = SendAmountRequest.newBuilder().setSourceKey(ByteString.copyFrom(origKey.getEncoded()))
                    .setDestinationKey(ByteString.copyFrom(destKey.getEncoded()))
                    .setAmount(amount)
                    .setSignature(ByteString.copyFrom(signature))
                    .setNonce(nonce)
                    .setTimestamp(timestamp)
                    .build();
            SendAmountResponse response = frontend.sendAmount(req);

            boolean ack = response.getAck();
            long recvTimestamp = response.getRecvTimestamp();
            long newTimestamp = response.getNewTimestamp();
            long newNonce = response.getNonce();
            ByteString sig = response.getSignature();
            byte[] newSignature = new byte[256];
            sig.copyTo(newSignature,0);

            //System.out.println("Ack: " + ack + "recv " + recvTimestamp + "new " + newTimestamp + "newNonce " + newNonce);
            String newMessage = String.valueOf(ack) + String.valueOf(newNonce) + timestamp + newTimestamp;

            if (validateResponse(getPublicKey("server"), newMessage, newSignature) && ack && recvTimestamp == timestamp && newTimestamp - timestamp < 600 && nonce + 1 == newNonce)
                System.out.println("Sent " + amount + " from " + senderAccount + " to " + receiverAccount);
            else
                System.out.println("WARNING! Invalid message from server. Someone might be intercepting your messages with the server.");


        } catch (StatusRuntimeException e) {
            printError(e);
        } catch (Exception e){
            System.out.println("Error while sending amount");
            e.printStackTrace();
        }
    }

    void check_account(String accountName){
        try {
            PublicKey key = getPublicKey(accountName);
            CheckAccountRequest req = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(key.getEncoded())).build();
            CheckAccountResponse res = frontend.checkAccount(req);

            int balance = res.getBalance();
            int pendentAmount = res.getPendentAmount();
            String transfers = res.getPendentTransfers();
            ByteString sig = res.getSignature();
            byte[] newSignature = new byte[256];
            sig.copyTo(newSignature,0);

            String newMessage = String.valueOf(balance) + pendentAmount + transfers;

            if(validateResponse(getPublicKey("server"), newMessage, newSignature))
                System.out.println("Account Status:\n\t" +
                                        "- Balance: " + balance +
                                        "\n\t- On hold amount to send: " + pendentAmount +
                                        "\n\t- Pending transfers:" + transfers.replaceAll("-", "\n\t\t-"));
            else
                System.out.println("WARNING! Invalid message from server. Someone might be intercepting your messages with the server.");

        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    void receive_amount(String accountName, String password){
        try {
            long nonce = new SecureRandom().nextLong();
            long timestamp = System.currentTimeMillis() / 1000;

            PublicKey key = getPublicKey(accountName);

            String message = key.toString() + nonce + timestamp;

            byte[] signature = encrypt(accountName, message, password);

            ReceiveAmountRequest req = ReceiveAmountRequest.newBuilder()
                    .setPublicKey(ByteString.copyFrom(key.getEncoded()))
                    .setSignature(ByteString.copyFrom(signature))
                    .setNonce(nonce)
                    .setTimestamp(timestamp)
                    .build();
            ReceiveAmountResponse response = frontend.receiveAmount(req);

            int recvAmount = response.getRecvAmount();
            long recvTimestamp = response.getRecvTimestamp();
            long newTimestamp = response.getNewTimestamp();
            long newNonce = response.getNonce();
            ByteString sig = response.getSignature();
            byte[] newSignature = new byte[256];
            sig.copyTo(newSignature, 0);

            String newMessage = String.valueOf(recvAmount) + String.valueOf(newNonce) + timestamp + newTimestamp;

            if (validateResponse(getPublicKey("server"), newMessage, newSignature) &&
                    recvTimestamp == timestamp && newTimestamp - timestamp < 600 && nonce + 1 == newNonce)
                System.out.println("Amount deposited to your account: " + recvAmount);
            else
                System.out.println("WARNING! Invalid message from server. Someone might be intercepting your messages with the server.");

        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    void audit(String accountName){
        try {
            PublicKey key = getPublicKey(accountName);
            AuditRequest req = AuditRequest.newBuilder().setPublicKey(ByteString.copyFrom(key.getEncoded())).build();
            AuditResponse res = frontend.auditResponse(req);

            String transfers = res.getTransferHistory();
            ByteString sig = res.getSignature();
            byte[] newSignature = new byte[256];
            sig.copyTo(newSignature,0);

            if(validateResponse(getPublicKey("server"), transfers, newSignature))
                System.out.println("Total transfers:" + transfers.replaceAll("-", "\n\t-"));
            else
                System.out.println("WARNING! Invalid message from server. Someone might be intercepting your messages with the server.");

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
            ks.load(new FileInputStream(client_path + username +".jks"), password.toCharArray());

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

    private boolean validateResponse(PublicKey pubKey, String message, byte[] signature)
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

}