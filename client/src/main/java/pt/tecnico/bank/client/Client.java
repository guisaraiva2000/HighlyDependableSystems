package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.client.exceptions.AccountAlreadyExistsException;
import pt.tecnico.bank.client.exceptions.InvalidAmountException;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.client.frontend.Frontend;
import pt.tecnico.bank.server.grpc.Server.*;

import java.security.Key;
import java.security.PublicKey;
import java.security.SecureRandom;

public class Client {

    public final String ANSI_GREEN = "\033[0;32m";
    public final String ANSI_RED = "\033[0;31m";

    private final Frontend frontend;
    private final Crypto crypto;
    private final int VALIDITY_INTERVAL = 60 * 10;

    public Client(String username, String password, int nByzantineServers) {
        this.crypto = new Crypto(username, password, true);
        this.frontend = new Frontend(nByzantineServers, this.crypto);
    }

    public String ping() {
        PingResponse res;
        try {
            PingRequest req = PingRequest.newBuilder().setInput("ola").build();
            res = frontend.ping(req);
        } catch (StatusRuntimeException e) {
            return handleError(e);
        }
        return ANSI_GREEN + res.getOutput();
    }

    public String open_account(String accountName) {
        try {
            if (crypto.accountExists(accountName))
                throw new AccountAlreadyExistsException();

            Key pubKey = crypto.getKey(accountName);
            byte[] encoded = pubKey.getEncoded();

            byte[] signature = crypto.encrypt(accountName, pubKey.toString());

            OpenAccountRequest req = OpenAccountRequest.newBuilder()
                    .setPublicKey(ByteString.copyFrom(encoded))
                    .setSignature(ByteString.copyFrom(signature))
                    .build();

            OpenAccountResponse res = frontend.openAccount(req);

            /*boolean ack = res.getAck();
            ByteString keyString = res.getPublicKey();
            byte[] key = new byte[698];
            keyString.copyTo(key, 0);
            byte[] newSignature = new byte[256];
            res.getSignature().copyTo(newSignature, 0);

            String message = ack + pubKey.toString();

           /* if (!crypto.validateMessage(crypto.getPublicKey(), message, newSignature))
                return mimWarn();*/

        } catch (StatusRuntimeException e) {
            e.printStackTrace();
            return handleError(e);
        } catch (AccountAlreadyExistsException e) {
            return ANSI_RED + e.getMessage();
        }
        return ANSI_GREEN + "Account with name " + accountName + " created";
    }

    public String send_amount(String senderAccount, String receiverAccount, int amount) {
        try {
            if (amount < 0)
                throw new InvalidAmountException();
            long nonce = new SecureRandom().nextLong();
            long timestamp = System.currentTimeMillis() / 1000;

            PublicKey origKey = crypto.getPublicKey(senderAccount);
            PublicKey destKey = crypto.getPublicKey(receiverAccount);

            String message = origKey.toString() + destKey.toString() + amount + nonce + timestamp;

            byte[] signature = crypto.encrypt(senderAccount, message);

            // send encrypted message instead of clear message
            SendAmountRequest req = SendAmountRequest.newBuilder().setSourceKey(ByteString.copyFrom(origKey.getEncoded()))
                    .setDestinationKey(ByteString.copyFrom(destKey.getEncoded()))
                    .setAmount(amount)
                    .setSignature(ByteString.copyFrom(signature))
                    .setNonce(nonce)
                    .setTimestamp(timestamp)
                    .build();
            SendAmountResponse response = frontend.sendAmount(req);

            /*boolean ack = response.getAck();
            long recvTimestamp = response.getRecvTimestamp();
            long newTimestamp = response.getNewTimestamp();
            long newNonce = response.getNonce();
            ByteString sig = response.getSignature();
            byte[] newSignature = new byte[256];
            sig.copyTo(newSignature, 0);

            String newMessage = ack + origKey.toString() + newNonce + timestamp + newTimestamp;

            /*if (!(crypto.validateMessage(crypto.getPublicKey(), newMessage, newSignature) &&
                    ack && recvTimestamp == timestamp && newTimestamp - timestamp < 600 && nonce + 1 == newNonce))
                return mimWarn();*/

        } catch (StatusRuntimeException e) {
            return handleError(e);
        } catch (InvalidAmountException e) {
            return ANSI_RED + e.getMessage();
        }
        return ANSI_GREEN + "Sent " + amount + " from " + senderAccount + " to " + receiverAccount;
    }

    public String check_account(String checkAccountName) {
        int balance, pendentAmount;
        String transfers;

        try {
            PublicKey key = crypto.getPublicKey(checkAccountName);
            CheckAccountRequest req = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFrom(key.getEncoded()))
                    .build();
            CheckAccountResponse res = frontend.checkAccount(req);

            balance = res.getBalance();
            pendentAmount = res.getPendentAmount();
            transfers = res.getPendentTransfers();
           /*long newTimestamp = res.getNewTimestamp();
            ByteString sig = res.getSignature();
            byte[] newSignature = new byte[256];
            sig.copyTo(newSignature, 0);

            String newMessage = String.valueOf(balance) + pendentAmount + transfers + newTimestamp;

            long timeValidity = System.currentTimeMillis() / 1000 - newTimestamp;

            /*if (!(crypto.validateResponse(crypto.getPublicKey("server"), newMessage, newSignature)
                    && timeValidity <= VALIDITY_INTERVAL))
                return mimWarn();*/

        } catch (StatusRuntimeException e) {
            return handleError(e);
        }
        return ANSI_GREEN + "Account Status:\n\t" +
                "- Balance: " + balance +
                "\n\t- On hold amount to send: " + pendentAmount +
                "\n\t- Pending transfers:" + transfers.replaceAll("-", "\n\t\t-");
    }

    public String receive_amount(String accountName) {
        int recvAmount;
        try {
            long nonce = new SecureRandom().nextLong();
            long timestamp = System.currentTimeMillis() / 1000;

            PublicKey key = crypto.getPublicKey(accountName);

            String message = key.toString() + nonce + timestamp;

            byte[] signature = crypto.encrypt(accountName, message);

            ReceiveAmountRequest req = ReceiveAmountRequest.newBuilder()
                    .setPublicKey(ByteString.copyFrom(key.getEncoded()))
                    .setSignature(ByteString.copyFrom(signature))
                    .setNonce(nonce)
                    .setTimestamp(timestamp)
                    .build();
            ReceiveAmountResponse response = frontend.receiveAmount(req);

            recvAmount = response.getRecvAmount();
            /*long recvTimestamp = response.getRecvTimestamp();
            long newTimestamp = response.getNewTimestamp();
            long newNonce = response.getNonce();
            ByteString sig = response.getSignature();
            byte[] newSignature = new byte[256];
            sig.copyTo(newSignature, 0);

            String newMessage = recvAmount + key.toString() + newNonce + timestamp + newTimestamp;

            /*if (!(crypto.validateResponse(crypto.getPublicKey("server"), newMessage, newSignature) &&
                    recvTimestamp == timestamp && newTimestamp - timestamp < 600 && nonce + 1 == newNonce))
                return mimWarn();*/

        } catch (StatusRuntimeException e) {
            return handleError(e);
        }

        return ANSI_GREEN + "Amount deposited to your account: " + recvAmount;
    }

    public String audit(String checkAccountName) {
        String transfers;
        try {
            PublicKey key = crypto.getPublicKey(checkAccountName);

            AuditRequest req = AuditRequest.newBuilder().setPublicKey(ByteString.copyFrom(key.getEncoded()))
                    .build();
            AuditResponse res = frontend.audit(req);

            transfers = res.getTransferHistory();
           /* long newTimestamp = res.getNewTimestamp();
            ByteString sig = res.getSignature();
            byte[] newSignature = new byte[256];
            sig.copyTo(newSignature, 0);

            long timeValidity = System.currentTimeMillis() / 1000 - newTimestamp;
            String newMessage = transfers + newTimestamp;

            /*if (!(crypto.validateResponse(crypto.getPublicKey("server"), newMessage, newSignature)
                    && timeValidity <= VALIDITY_INTERVAL))
                return mimWarn();*/

        } catch (StatusRuntimeException e) {
            return handleError(e);
        }

        return ANSI_GREEN + "Total transfers: " + transfers.replaceAll("-", "\n\t-");
    }

    private String handleError(StatusRuntimeException e) {
        if (e.getStatus().getDescription() != null && e.getStatus().getDescription().equals("io exception")) {
            return ANSI_RED + "Warn: Server not responding!";
        } else {
            return ANSI_RED + e.getStatus().getDescription();
        }
    }

    private String mimWarn() {
        return ANSI_RED + "WARNING! Invalid message from server. Someone might be intercepting your messages with the server.";
    }

    void close() {
        frontend.close();
    }
}