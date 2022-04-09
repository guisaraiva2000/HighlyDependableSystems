package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import pt.tecnico.bank.server.domain.exceptions.NonceAlreadyUsedException;
import pt.tecnico.bank.server.domain.exceptions.TimestampExpiredException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class SecurityHandler {

    private final String SERVER_PATH = System.getProperty("user.dir") + File.separator + "KEYS" + File.separator;
    private final String SERVER_PASS = "server";

    public SecurityHandler() {
    }


    PublicKey keyToBytes(ByteString pubKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] pubKeyBytes = new byte[294];
        pubKey.copyTo(pubKeyBytes, 0);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubKeyBytes));
    }

    void validateNonce(User user, long nonce, long timestamp)
            throws NonceAlreadyUsedException, TimestampExpiredException {
        user.getNonceManager().validateNonce(nonce, timestamp);
    }

    boolean validateMessage(PublicKey key, String message, byte[] signature) throws NoSuchAlgorithmException,
            InvalidKeyException, SignatureException {

        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initVerify(key);
        sign.update(message.getBytes(StandardCharsets.ISO_8859_1));
        return sign.verify(signature);
    }

    byte[] encrypt(String message) throws KeyStoreException, IOException, UnrecoverableKeyException,
            NoSuchAlgorithmException, CertificateException, InvalidKeyException, SignatureException {
        KeyStore ks = KeyStore.getInstance("JCEKS");
        ks.load(new FileInputStream(SERVER_PATH + SERVER_PASS + ".jks"), SERVER_PASS.toCharArray());

        PrivateKey privKey = (PrivateKey) ks.getKey(SERVER_PASS, SERVER_PASS.toCharArray());

        // SIGNATURE
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(privKey);

        sign.update(message.getBytes(StandardCharsets.ISO_8859_1));
        return sign.sign();
    }

    String[] createResponse(String[] message, long nonce, long timestamp) throws UnrecoverableKeyException,
            CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        long newTimestamp = System.currentTimeMillis() / 1000;

        String[] response = new String[message.length + 4];
        StringBuilder m = new StringBuilder();

        int i;
        for (i = 0; i < message.length; i++) {
            response[i] = message[i];
            m.append(message[i]);
        }

        m.append(nonce + 1).append(timestamp).append(newTimestamp);
        byte[] signServer = encrypt(m.toString());

        response[i] = String.valueOf(nonce + 1);
        response[i + 1] = String.valueOf(timestamp);
        response[i + 2] = String.valueOf(newTimestamp);
        response[i + 3] = new String(signServer, StandardCharsets.ISO_8859_1);

        return response;
    }

}
