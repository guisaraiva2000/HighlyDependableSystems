package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.client.exceptions.AccountAlreadyExistsException;
import pt.tecnico.bank.client.exceptions.InvalidAmountException;
import pt.tecnico.bank.client.frontend.Frontend;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.grpc.Server.*;

import java.security.Key;
import java.security.PublicKey;

public class Client {

    public final String ANSI_GREEN = "\033[0;32m";
    public final String ANSI_RED = "\033[0;31m";

    private final Frontend frontend;
    private final Crypto crypto;

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

        } catch (StatusRuntimeException e) {
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

            long nonce = crypto.generateNonce();
            long timestamp = crypto.generateTimestamp();

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
            long nonce = crypto.generateNonce();
            long timestamp = crypto.generateTimestamp();

            PublicKey key = crypto.getPublicKey(checkAccountName);

            CheckAccountRequest req = CheckAccountRequest.newBuilder()
                    .setNonce(nonce)
                    .setTimestamp(timestamp)
                    .setPublicKey(ByteString.copyFrom(key.getEncoded()))
                    .build();
            CheckAccountResponse res = frontend.checkAccount(req);

            balance = res.getBalance();
            pendentAmount = res.getPendentAmount();
            transfers = res.getPendentTransfers();

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
            long nonce = crypto.generateNonce();
            long timestamp = crypto.generateTimestamp();

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

        } catch (StatusRuntimeException e) {
            return handleError(e);
        }

        return ANSI_GREEN + "Amount deposited to your account: " + recvAmount;
    }

    public String audit(String checkAccountName) {
        String transfers;

        try {

            long nonce = crypto.generateNonce();
            long timestamp = crypto.generateTimestamp();

            PublicKey key = crypto.getPublicKey(checkAccountName);

            AuditRequest req = AuditRequest.newBuilder()
                    .setNonce(nonce)
                    .setTimestamp(timestamp)
                    .setPublicKey(ByteString.copyFrom(key.getEncoded()))
                    .build();
            AuditResponse res = frontend.audit(req);

            transfers = res.getTransferHistory();

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