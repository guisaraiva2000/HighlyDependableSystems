package pt.tecnico.bank.tester;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.grpc.Server.OpenAccountRequest;
import pt.tecnico.bank.server.grpc.Server.OpenAccountResponse;

import java.security.Key;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OpenAccountTestsIT extends BaseIT {


    @BeforeAll
    public static void oneTimeSetUp() {
        cryptos.put("open1", new Crypto("open1", "pass1", true));
        cryptos.put("open2", new Crypto("open2", "pass2", true));
        cryptos.put("open3", new Crypto("open3", "pass3", true));
    }


    @Test
    public void openAccountSuccess() {
        String username = "open1";

        Key pubKey = cryptos.get(username).generateKeyStore(username);
        byte[] encoded = pubKey.getEncoded();
        int initWid = 0;
        int initBalance = 100;
        byte[] pairSignature = cryptos.get(username).encrypt(username, initWid + String.valueOf(initBalance));
        String m = username + initWid + initBalance + Arrays.toString(pairSignature) + pubKey;
        byte[] signature = cryptos.get(username).encrypt(username, m);

        OpenAccountRequest req = OpenAccountRequest.newBuilder()
                .setUsername(username)
                .setInitWid(initWid)
                .setInitBalance(initBalance)
                .setPairSignature(ByteString.copyFrom(pairSignature))
                .setPublicKey(ByteString.copyFrom(encoded))
                .setSignature(ByteString.copyFrom(signature))
                .build();

        OpenAccountResponse res = bcsFrontend.openAccount(req);

        assertEquals("open1", res.getUsername());
    }

    @Test
    public void openAccountInvalidSignature() {
        String username = "open2";

        Key pubKey = cryptos.get(username).generateKeyStore(username);
        byte[] encoded = pubKey.getEncoded();
        int initWid = 0;
        int initBalance = 100;
        byte[] pairSignature = cryptos.get(username).encrypt(username, initWid + String.valueOf(initBalance));
        String m = username + initWid + initBalance + Arrays.toString(pairSignature) + pubKey;

        byte[] signature = cryptos.get(username).encrypt(username, "WRONG MESSAGE TO ENCRYPT");

        OpenAccountRequest req = OpenAccountRequest.newBuilder()
                .setUsername(username)
                .setInitWid(initWid)
                .setInitBalance(initBalance)
                .setPairSignature(ByteString.copyFrom(pairSignature))
                .setPublicKey(ByteString.copyFrom(encoded))
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertEquals("INTERNAL: INTERNAL: ERROR: Either message was altered or the signature is not correct.",
                assertThrows(
                        StatusRuntimeException.class,
                        () -> bcsFrontend.openAccount(req)
                        ).getMessage()
        );
    }

    @Test
    public void openAccountInvalidPairSignature() {
        String username = "open3";

        Key pubKey = cryptos.get(username).generateKeyStore(username);
        byte[] encoded = pubKey.getEncoded();
        int initWid = 0;
        int initBalance = 100;
        byte[] pairSignature = cryptos.get(username).encrypt(username, "WRONG MESSAGE TO ENCRYPT");
        String m = username + initWid + initBalance + Arrays.toString(pairSignature) + pubKey;

        byte[] signature = cryptos.get(username).encrypt(username, m);

        OpenAccountRequest req = OpenAccountRequest.newBuilder()
                .setUsername(username)
                .setInitWid(initWid)
                .setInitBalance(initBalance)
                .setPairSignature(ByteString.copyFrom(pairSignature))
                .setPublicKey(ByteString.copyFrom(encoded))
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertEquals("INTERNAL: INTERNAL: ERROR: Either message was altered or the signature is not correct.",
                assertThrows(
                        StatusRuntimeException.class,
                        () -> bcsFrontend.openAccount(req)
                ).getMessage()
        );
    }


}
