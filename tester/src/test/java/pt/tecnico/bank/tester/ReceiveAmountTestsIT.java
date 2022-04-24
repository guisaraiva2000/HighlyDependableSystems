package pt.tecnico.bank.tester;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.server.grpc.Server;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReceiveAmountTestsIT extends BaseIT {


    @BeforeAll
    public static void oneTimeSetUp() {
        openUser("sender1", "pass1");
        openUser("receiver1", "pass2");
        send();
    }

    private static void send() {
        String sender = "sender1", receiver = "receiver1";

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

        byte[] signature = cryptos.get(sender).encrypt(sender, m);

        Server.SendAmountRequest req = Server.SendAmountRequest.newBuilder()
                .setTransaction(transaction)
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .setBalance(balanceToSend)
                .setPairSignature(ByteString.copyFrom(pairSignature))
                .setSignature(ByteString.copyFrom(signature))
                .build();

        bcsFrontend.sendAmount(req);
    }

    @Test
    public void receiveAmountInvalidTransactionSignature() {

        String sender = "sender1", receiver = "receiver1";

        PublicKey senderKey = cryptos.get(sender).getPublicKey(sender);
        PublicKey receiverKey = cryptos.get(sender).getPublicKey(receiver);

        int amountToReceive = 20;

        int balance = 100;
        int wid = 1;

        List<Server.Transaction> transactions = new ArrayList<>();

        String transactionMessage =
                amountToReceive + sender + receiver + senderKey + receiverKey + wid + false;

        byte[] transactionSignature = cryptos.get(receiver).encrypt(receiver, "WRONG MESSAGE TO ENCRYPT");

        transactions.add(
                Server.Transaction.newBuilder()
                        .setAmount(amountToReceive)
                        .setSenderUsername(sender)
                        .setReceiverUsername(receiver)
                        .setSenderKey(ByteString.copyFrom(senderKey.getEncoded()))
                        .setReceiverKey(ByteString.copyFrom(receiverKey.getEncoded()))
                        .setWid(wid)
                        .setSent(false)
                        .setSignature(ByteString.copyFrom(transactionSignature))
                        .build()
        );


        long nonce = cryptos.get(receiver).generateNonce();
        long timestamp = cryptos.get(receiver).generateTimestamp();

        int balanceToSend = balance + amountToReceive;

        byte[] pairSignature = cryptos.get(receiver).encrypt(receiver,  String.valueOf(wid) + balanceToSend);

        String m = transactions + receiverKey.toString() + nonce + timestamp + wid + balanceToSend + Arrays.toString(pairSignature);

        Server.ReceiveAmountRequest recvReq = Server.ReceiveAmountRequest.newBuilder()
                .addAllPendingTransactions(transactions)
                .setPublicKey(ByteString.copyFrom(receiverKey.getEncoded()))
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .setWid(wid)
                .setBalance(balanceToSend)
                .setPairSignature(ByteString.copyFrom(pairSignature))
                .setSignature(ByteString.copyFrom(cryptos.get(receiver).encrypt(receiver, m)))
                .build();

        assertEquals("INTERNAL: INTERNAL: ERROR: Either message was altered or the signature is not correct.",
                assertThrows(
                        StatusRuntimeException.class,
                        () -> bcsFrontend.receiveAmount(recvReq)
                ).getMessage()
        );

    }

    @Test
    public void receiveAmountInvalidPairSignature() {

        String sender = "sender1", receiver = "receiver1";

        PublicKey senderKey = cryptos.get(sender).getPublicKey(sender);
        PublicKey receiverKey = cryptos.get(sender).getPublicKey(receiver);

        int amountToReceive = 20;

        int balance = 100;
        int wid = 1;

        List<Server.Transaction> transactions = new ArrayList<>();

        String transactionMessage =
                amountToReceive + sender + receiver + senderKey + receiverKey + wid + false;

        byte[] transactionSignature = cryptos.get(receiver).encrypt(receiver, transactionMessage);

        transactions.add(
                Server.Transaction.newBuilder()
                        .setAmount(amountToReceive)
                        .setSenderUsername(sender)
                        .setReceiverUsername(receiver)
                        .setSenderKey(ByteString.copyFrom(senderKey.getEncoded()))
                        .setReceiverKey(ByteString.copyFrom(receiverKey.getEncoded()))
                        .setWid(wid)
                        .setSent(false)
                        .setSignature(ByteString.copyFrom(transactionSignature))
                        .build()
        );


        long nonce = cryptos.get(receiver).generateNonce();
        long timestamp = cryptos.get(receiver).generateTimestamp();

        int balanceToSend = balance + amountToReceive;

        byte[] pairSignature = cryptos.get(receiver).encrypt(receiver,  "WRONG MESSAGE TO ENCRYPT");

        String m = transactions + receiverKey.toString() + nonce + timestamp + wid + balanceToSend + Arrays.toString(pairSignature);

        Server.ReceiveAmountRequest recvReq = Server.ReceiveAmountRequest.newBuilder()
                .addAllPendingTransactions(transactions)
                .setPublicKey(ByteString.copyFrom(receiverKey.getEncoded()))
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .setWid(wid)
                .setBalance(balanceToSend)
                .setPairSignature(ByteString.copyFrom(pairSignature))
                .setSignature(ByteString.copyFrom(cryptos.get(receiver).encrypt(receiver, m)))
                .build();

        assertEquals("INTERNAL: INTERNAL: ERROR: Either message was altered or the signature is not correct.",
                assertThrows(
                        StatusRuntimeException.class,
                        () -> bcsFrontend.receiveAmount(recvReq)
                ).getMessage()
        );

    }

    @Test
    public void receiveAmountInvalidSignature() {

        String sender = "sender1", receiver = "receiver1";

        PublicKey senderKey = cryptos.get(sender).getPublicKey(sender);
        PublicKey receiverKey = cryptos.get(sender).getPublicKey(receiver);

        int amountToReceive = 20;

        int balance = 100;
        int wid = 1;

        List<Server.Transaction> transactions = new ArrayList<>();

        String transactionMessage =
                amountToReceive + sender + receiver + senderKey + receiverKey + wid + false;

        byte[] transactionSignature = cryptos.get(receiver).encrypt(receiver, transactionMessage);

        transactions.add(
                Server.Transaction.newBuilder()
                        .setAmount(amountToReceive)
                        .setSenderUsername(sender)
                        .setReceiverUsername(receiver)
                        .setSenderKey(ByteString.copyFrom(senderKey.getEncoded()))
                        .setReceiverKey(ByteString.copyFrom(receiverKey.getEncoded()))
                        .setWid(wid)
                        .setSent(false)
                        .setSignature(ByteString.copyFrom(transactionSignature))
                        .build()
        );


        long nonce = cryptos.get(receiver).generateNonce();
        long timestamp = cryptos.get(receiver).generateTimestamp();

        int balanceToSend = balance + amountToReceive;

        byte[] pairSignature = cryptos.get(receiver).encrypt(receiver,  String.valueOf(wid) + balanceToSend);

        String m = transactions + receiverKey.toString() + nonce + timestamp + wid + balanceToSend + Arrays.toString(pairSignature);

        Server.ReceiveAmountRequest recvReq = Server.ReceiveAmountRequest.newBuilder()
                .addAllPendingTransactions(transactions)
                .setPublicKey(ByteString.copyFrom(receiverKey.getEncoded()))
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .setWid(wid)
                .setBalance(balanceToSend)
                .setPairSignature(ByteString.copyFrom(pairSignature))
                .setSignature(ByteString.copyFrom(cryptos.get(receiver).encrypt(receiver, "WRONG MESSAGE TO ENCRYPT")))
                .build();

        assertEquals("INTERNAL: INTERNAL: ERROR: Either message was altered or the signature is not correct.",
                assertThrows(
                        StatusRuntimeException.class,
                        () -> bcsFrontend.receiveAmount(recvReq)
                ).getMessage()
        );

    }
}
