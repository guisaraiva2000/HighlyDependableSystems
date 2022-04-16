package pt.tecnico.bank.crypto;

import com.google.protobuf.ByteString;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Calendar;
import java.util.Date;

public class Crypto {

    private final String alias;
    private final String password;
    private final String path;
    private final String certPath;

    public Crypto(String alias, String password, boolean isClient) {
        this.alias = alias;
        this.password = password;

        String path = isClient ? "Clients" : "Servers";

        String sep = File.separator;
        this.path =  ".." + sep + "Crypto" + sep + path + sep + alias + sep;
        this.certPath = ".." + sep + "Crypto" + sep + "Certificates" + sep;
    }


    public byte[] encrypt(String accountName, String message) {

        try {

            KeyStore ks = KeyStore.getInstance("JCEKS");
            ks.load(new FileInputStream(this.path + this.alias + ".jks"), this.password.toCharArray());

            PrivateKey privKey = (PrivateKey) ks.getKey(accountName, this.password.toCharArray());

            // SIGNATURE
            Signature sign = Signature.getInstance("SHA256withRSA");
            sign.initSign(privKey);

            sign.update(message.getBytes(StandardCharsets.ISO_8859_1));
            return sign.sign();

        } catch (UnrecoverableKeyException | CertificateException | KeyStoreException | IOException
                | NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {

            e.printStackTrace();
        }

        return null;

    }


    public boolean accountExists(String alias) {
        File file = new File(this.certPath + alias + ".cert");
        return file.exists();
    }


    public Key getKey(String accountName) {

        try {

            KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
            rsaKeyGen.initialize(2048);

            KeyPair rsaKeyPair = rsaKeyGen.generateKeyPair();

            Key pubKey = rsaKeyPair.getPublic();
            Key privKey = rsaKeyPair.getPrivate();

            KeyStore ks = KeyStore.getInstance("JCEKS");

            String ksFile = this.path + this.alias + ".jks";
            File file = new File(ksFile);

            if (!file.exists()) {
                try {
                    Files.createDirectories(Paths.get(ksFile).getParent());
                    Files.createFile(Paths.get(ksFile));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                ks.load(null, this.password.toCharArray());
            } else {
                ks.load(new FileInputStream(file), this.password.toCharArray());
            }

            X509Certificate[] certificateChain = new X509Certificate[1];
            certificateChain[0] = selfSign(rsaKeyPair, accountName);

            ks.setKeyEntry(accountName, privKey, this.password.toCharArray(), certificateChain);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                ks.store(fos, this.password.toCharArray());
            }

            try (FileOutputStream fos = new FileOutputStream(this.path + "public.key")) {
                fos.write(pubKey.getEncoded());
            }

            try (FileOutputStream fos = new FileOutputStream(this.path + "private.key")) {
                fos.write(privKey.getEncoded());
            }
            return pubKey;

        } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException | IOException e) {

            e.printStackTrace();
        }

        return null;
    }


    public X509Certificate selfSign(KeyPair keyPair, String subjectDN) {

        try {

            X509Certificate certificate;
            Provider bcProvider = new BouncyCastleProvider();
            Security.addProvider(bcProvider);

            long now = System.currentTimeMillis();
            Date startDate = new Date(now);

            X500Name dnName = new X500Name("CN=" + subjectDN);
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

            File file = new File(this.certPath + subjectDN + ".cert");
            file.createNewFile();
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(buf);
            }
            return certificate;

        } catch (CertificateException | IOException | OperatorCreationException e) {

            e.printStackTrace();
        }

        return null;

    }


    public PublicKey getPublicKey(String alias) {

        if (!accountExists(alias)) return null;

        try {

            CertificateFactory fac = CertificateFactory.getInstance("X509");
            FileInputStream in = new FileInputStream(this.certPath + alias + ".cert");
            X509Certificate cert = (X509Certificate) fac.generateCertificate(in);
            return cert.getPublicKey();

        } catch (CertificateException | FileNotFoundException e) {

            e.printStackTrace();
        }
        return null;
    }


    public PublicKey bytesToKey(ByteString pubKey) {

        try {

            byte[] pubKeyBytes = new byte[294];
            pubKey.copyTo(pubKeyBytes, 0);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubKeyBytes));

        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {

            e.printStackTrace();
        }

        return null;
    }

    public boolean validateMessage(PublicKey key, String message, byte[] signature)  {

        Signature sign;

        try {

            sign = Signature.getInstance("SHA256withRSA");
            sign.initVerify(key);
            sign.update(message.getBytes(StandardCharsets.ISO_8859_1));
            return sign.verify(signature);

        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {

            e.printStackTrace();
        }

        return false;
    }
}
