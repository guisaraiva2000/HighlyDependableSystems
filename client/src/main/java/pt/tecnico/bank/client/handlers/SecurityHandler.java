package pt.tecnico.bank.client.handlers;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

public class SecurityHandler {

    private final String username;
    private final String CERT_PATH = System.getProperty("user.dir") + "\\CERTIFICATES\\";
    private final String client_path;

    public SecurityHandler(String username) {
        this.username = username;
        this.client_path = System.getProperty("user.dir") + "\\CLIENTS\\" + username + "\\";;
    }

    public byte[] encrypt(String alias, String message, String password){
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

    public Key getKey(String accountName, String password) throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException {
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

        try (FileOutputStream fos = new FileOutputStream(client_path + "public.key")) {
            fos.write(privKey.getEncoded());
        }
        return pubKey;
    }

    public X509Certificate selfSign(KeyPair keyPair, String subjectDN)
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

    public PublicKey getPublicKey(String alias) throws CertificateException, FileNotFoundException {
        CertificateFactory fac = CertificateFactory.getInstance("X509");
        FileInputStream in = new FileInputStream(CERT_PATH + alias + ".cert");
        X509Certificate cert = (X509Certificate) fac.generateCertificate(in);
        return cert.getPublicKey();
    }

    public boolean validateResponse(PublicKey pubKey, String message, byte[] signature)
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