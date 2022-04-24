package pt.tecnico.bank.tester;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.server.grpc.Server;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AuditWriteBackTestsIT extends BaseIT {


    @BeforeAll
    public static void oneTimeSetUp() {
        openUser("auditee1", "pass1");
        openUser("audited1", "pass2");
    }

    @Test
    public void auditWriteBackInvalidSignature() {
        String auditee = "auditee1", audited = "audited1";

        long nonce = cryptos.get(auditee).generateNonce();
        long timestamp = cryptos.get(auditee).generateTimestamp();

        PublicKey clientKey = cryptos.get(auditee).getPublicKey(auditee);
        PublicKey auditKey = cryptos.get(auditee).getPublicKey(audited);

        // --------------------- Proof of Work ---------------------

        Server.ProofOfWorkResponse proofOfWorkResponse = requestChallenge();

        String sName = proofOfWorkResponse.getServerName();
        byte[] powChallenge = cryptos.get(auditee).byteStringToByteArray(proofOfWorkResponse.getChallenge());

        long pow = cryptos.get(auditee).generateProofOfWork(powChallenge);

        Map<String, Long> pows = new HashMap<>();

        pows.put(sName, pow);

        // ---------------------------------------------------------


        String m = clientKey.toString() + auditKey + nonce + timestamp + pows + 1;

        Server.AuditRequest req = Server.AuditRequest.newBuilder()
                .setClientKey(ByteString.copyFrom(clientKey.getEncoded()))
                .setAuditKey(ByteString.copyFrom(auditKey.getEncoded()))
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .putAllPows(pows)
                .setRid(1)
                .setSignature(ByteString.copyFrom(cryptos.get(auditee).encrypt(auditee, m)))
                .build();

        Server.AuditResponse res = bcsFrontend.audit(req);

        nonce = cryptos.get(auditee).generateNonce();
        timestamp = cryptos.get(auditee).generateTimestamp();

        String message = clientKey.toString() + auditKey + nonce + timestamp + res.getTransactionsList();

        byte[] signature = cryptos.get(auditee).encrypt(auditee, "WRONG MESSGAGE TO ENCRYPT");

        Server.AuditWriteBackRequest reqBack = Server.AuditWriteBackRequest.newBuilder()
                .setClientKey(ByteString.copyFrom(clientKey.getEncoded()))
                .setAuditKey(ByteString.copyFrom(auditKey.getEncoded()))
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .addAllTransactions(res.getTransactionsList())
                .setSignature(ByteString.copyFrom(signature))
                .build();


        assertEquals("INTERNAL: INTERNAL: ERROR: Either message was altered or the signature is not correct.",
                assertThrows(
                        StatusRuntimeException.class,
                        () -> bcsFrontend.auditWriteBack(reqBack)
                ).getMessage()
        );


    }

    public Server.ProofOfWorkResponse requestChallenge() {
        String auditee = "auditee1";

        PublicKey key = cryptos.get(auditee).getPublicKey(auditee);

        long nonce = cryptos.get(auditee).generateNonce();
        long timestamp = cryptos.get(auditee).generateTimestamp();

        byte[] signature = cryptos.get(auditee).encrypt(auditee, key.toString() + nonce + timestamp);

        Server.ProofOfWorkRequest req = Server.ProofOfWorkRequest.newBuilder()
                .setPublicKey(ByteString.copyFrom(key.getEncoded()))
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .setSignature(ByteString.copyFrom(signature))
                .build();

        return bcsFrontend.pow(req);
    }


}
