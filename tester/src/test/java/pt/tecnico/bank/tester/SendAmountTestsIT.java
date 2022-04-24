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

public class SendAmountTestsIT extends BaseIT {


    @BeforeAll
    public static void oneTimeSetUp() {
        openUser("sender", "pass1");
        openUser("receiver", "pass2");
    }

    @Test
    public void sendAmountInvalidTransactionSignature() {
        String sender = "sender", receiver = "receiver";

        long nonce = cryptos.get(sender).generateNonce();
        long timestamp = cryptos.get(sender).generateTimestamp();

        PublicKey senderKey = cryptos.get(sender).getPublicKey(sender);
        PublicKey receiverKey = cryptos.get(sender).getPublicKey(receiver);

        int amount = 20;
        int widToSend = 1;
        int balanceToSend = 100 - amount;

        String transactionMessage = amount + sender + receiver + senderKey + receiverKey + widToSend + true;
        byte[] transactionSignature = cryptos.get(sender).encrypt(sender, "WRONG MESSAGE TO ENCRYPT");

        Server.Transaction transaction = Server.Transaction.newBuilder()
                .setAmount(amount)
                .setSenderUsername(sender)
                .setReceiverUsername(receiver)
                .setSenderKey(ByteString.copyFrom(senderKey.getEncoded()))
                .setReceiverKey(ByteString.copyFrom(receiverKey.getEncoded()))
                .setWid(widToSend)
                .setSent(true)
                .setSignature(ByteString.copyFrom(transactionSignature))
                .build();

        byte[] pairSignature = cryptos.get(sender).encrypt(sender,  String.valueOf(widToSend) + balanceToSend);

        String m = transaction.toString() + nonce + timestamp + widToSend + balanceToSend + Arrays.toString(pairSignature);

        byte[] signature = cryptos.get(sender).encrypt(sender, m);

        Server.SendAmountRequest req = Server.SendAmountRequest.newBuilder()
                .setTransaction(transaction)
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .setBalance(balanceToSend)
                .setPairSignature(ByteString.copyFrom(pairSignature))
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertEquals("INTERNAL: INTERNAL: ERROR: Either message was altered or the signature is not correct.",
                assertThrows(
                        StatusRuntimeException.class,
                        () -> bcsFrontend.sendAmount(req)
                ).getMessage()
        );
    }

    @Test
    public void sendAmountInvalidPairSignature() {

        String sender = "sender", receiver = "receiver";

        long nonce = cryptos.get(sender).generateNonce();
        long timestamp = cryptos.get(sender).generateTimestamp();

        PublicKey senderKey = cryptos.get(sender).getPublicKey(sender);
        PublicKey receiverKey = cryptos.get(sender).getPublicKey(receiver);

        int amount = 20;
        int widToSend = 1;
        int balanceToSend = 100 - amount;

        String transactionMessage = amount + sender + receiver + senderKey + receiverKey + widToSend + true;
        byte[] transactionSignature = cryptos.get(sender).encrypt(sender, transactionMessage);

        Server.Transaction transaction = Server.Transaction.newBuilder()
                .setAmount(amount)
                .setSenderUsername(sender)
                .setReceiverUsername(receiver)
                .setSenderKey(ByteString.copyFrom(senderKey.getEncoded()))
                .setReceiverKey(ByteString.copyFrom(receiverKey.getEncoded()))
                .setWid(widToSend)
                .setSent(true)
                .setSignature(ByteString.copyFrom(transactionSignature))
                .build();

        byte[] pairSignature = cryptos.get(sender).encrypt(sender,  "WRONG MESSAGE TO ENCRYPT");

        String m = transaction.toString() + nonce + timestamp + widToSend + balanceToSend + Arrays.toString(pairSignature);

        byte[] signature = cryptos.get(sender).encrypt(sender, m);

        Server.SendAmountRequest req = Server.SendAmountRequest.newBuilder()
                .setTransaction(transaction)
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .setBalance(balanceToSend)
                .setPairSignature(ByteString.copyFrom(pairSignature))
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertEquals("INTERNAL: INTERNAL: ERROR: Either message was altered or the signature is not correct.",
                assertThrows(
                        StatusRuntimeException.class,
                        () -> bcsFrontend.sendAmount(req)
                ).getMessage()
        );
    }

    @Test
    public void sendAmountInvalidSignature() {
        String sender = "sender", receiver = "receiver";

        long nonce = cryptos.get(sender).generateNonce();
        long timestamp = cryptos.get(sender).generateTimestamp();

        PublicKey senderKey = cryptos.get(sender).getPublicKey(sender);
        PublicKey receiverKey = cryptos.get(sender).getPublicKey(receiver);

        int amount = 20;
        int widToSend = 1;
        int balanceToSend = 100 - amount;

        String transactionMessage = amount + sender + receiver + senderKey + receiverKey + widToSend + true;
        byte[] transactionSignature = cryptos.get(sender).encrypt(sender, transactionMessage);

        Server.Transaction transaction = Server.Transaction.newBuilder()
                .setAmount(amount)
                .setSenderUsername(sender)
                .setReceiverUsername(receiver)
                .setSenderKey(ByteString.copyFrom(senderKey.getEncoded()))
                .setReceiverKey(ByteString.copyFrom(receiverKey.getEncoded()))
                .setWid(widToSend)
                .setSent(true)
                .setSignature(ByteString.copyFrom(transactionSignature))
                .build();

        byte[] pairSignature = cryptos.get(sender).encrypt(sender,  String.valueOf(widToSend) + balanceToSend);

        String m = transaction.toString() + nonce + timestamp + widToSend + balanceToSend + Arrays.toString(pairSignature);

        byte[] signature = cryptos.get(sender).encrypt(sender, "WRONG MESSAGE TO ENCRYPT");

        Server.SendAmountRequest req = Server.SendAmountRequest.newBuilder()
                .setTransaction(transaction)
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .setBalance(balanceToSend)
                .setPairSignature(ByteString.copyFrom(pairSignature))
                .setSignature(ByteString.copyFrom(signature))
                .build();

        assertEquals("INTERNAL: INTERNAL: ERROR: Either message was altered or the signature is not correct.",
                assertThrows(
                        StatusRuntimeException.class,
                        () -> bcsFrontend.sendAmount(req)
                ).getMessage()
        );
    }

}
