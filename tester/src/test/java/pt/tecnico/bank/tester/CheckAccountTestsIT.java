package pt.tecnico.bank.tester;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.server.grpc.Server;

import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CheckAccountTestsIT extends BaseIT {


    @BeforeAll
    public static void oneTimeSetUp() {
        openUser("checker", "pass1");
        openUser("checked", "pass2");
    }

    @Test
    public void checkAccountInvalidSignature() {
        String username = "checker";
        String clientToCheck = "checked";

        long nonce = cryptos.get(username).generateNonce();
        long timestamp = cryptos.get(username).generateTimestamp();

        PublicKey clientKey = cryptos.get(username).getPublicKey(username);
        PublicKey key = cryptos.get(username).getPublicKey(clientToCheck);

        String m = clientKey.toString() + key + nonce + timestamp + 1;

        Server.CheckAccountRequest checkReq = Server.CheckAccountRequest.newBuilder()
                .setClientKey(ByteString.copyFrom(clientKey.getEncoded()))
                .setCheckKey(ByteString.copyFrom(key.getEncoded()))
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .setRid(1)
                .setSignature(ByteString.copyFrom(cryptos.get(username).encrypt(username, "WRONG MESSAGE TO ENCRYPT")))
                .build();


        assertEquals("INTERNAL: INTERNAL: ERROR: Either message was altered or the signature is not correct.",
                assertThrows(
                        StatusRuntimeException.class,
                        () -> bcsFrontend.checkAccount(checkReq)
                ).getMessage()
        );

    }


}
