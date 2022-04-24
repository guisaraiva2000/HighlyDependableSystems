package pt.tecnico.bank.tester;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.server.grpc.Server;

import java.security.PublicKey;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CheckWriteBackTestsIT extends BaseIT {


    @BeforeAll
    public static void oneTimeSetUp() {
        openUser("checker1", "pass1");
        openUser("checker2", "pass2");
    }


    @Test
    public void checkAccountWriteBackInvalidSignature() {
        String username = "checker1";
        String clientToCheck = "checker2";

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
                .setSignature(ByteString.copyFrom(cryptos.get(username).encrypt(username, m)))
                .build();


        Server.CheckAccountResponse res = bcsFrontend.checkAccount(checkReq);

        nonce = cryptos.get(username).generateNonce();
        timestamp = cryptos.get(username).generateTimestamp();

        clientKey = cryptos.get(username).getPublicKey(username);
        key = cryptos.get(username).getPublicKey(clientToCheck);

        String message = clientKey.toString() + key + nonce + timestamp + res.getPendingTransactionsList()
                + res.getBalance() + res.getWid() + Arrays.toString(cryptos.get(username).byteStringToByteArray(res.getPairSignature()));

        byte[] signature = cryptos.get(username).encrypt(username, "WRONG MESSAGE TO SIGN");

        Server.CheckAccountWriteBackRequest req = Server.CheckAccountWriteBackRequest.newBuilder()
                .setClientKey(ByteString.copyFrom(clientKey.getEncoded()))
                .setCheckKey(ByteString.copyFrom(key.getEncoded()))
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .addAllPendingTransactions(res.getPendingTransactionsList())
                .setBalance(res.getBalance())
                .setWid(res.getWid())
                .setPairSign(res.getPairSignature())
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertEquals("INTERNAL: INTERNAL: ERROR: Either message was altered or the signature is not correct.",
                assertThrows(
                        StatusRuntimeException.class,
                        () -> bcsFrontend.checkAccountWriteBack(req)
                ).getMessage()
        );
    }
}
