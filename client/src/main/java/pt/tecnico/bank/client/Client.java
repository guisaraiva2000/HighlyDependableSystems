package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.client.exceptions.AccountAlreadyExistsException;
import pt.tecnico.bank.client.exceptions.InvalidAmountException;
import pt.tecnico.bank.client.frontend.ClientServerFrontend;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.client.exceptions.AccountDoesNotExistsException;
import pt.tecnico.bank.server.grpc.Server.*;

import java.security.Key;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Client {

    public final String ANSI_GREEN = "\033[0;32m";
    public final String ANSI_RED = "\033[0;31m";

    private final ClientServerFrontend frontend;
    private final Crypto crypto;
    private final String username;

    private int rid;

    public Client(String username, String password, int nByzantineServers) {
        this.username = username;
        this.crypto = new Crypto(username, password, true);
        this.frontend = new ClientServerFrontend(nByzantineServers, this.crypto);
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

    public String open_account() {
        try {
            if (crypto.accountExists( this.username))
                throw new AccountAlreadyExistsException();

            Key pubKey = crypto.generateKeyStore(this.username);
            byte[] encoded = pubKey.getEncoded();

            int initWid = 0;
            int initBalance = 100; // default value

            // signature of (wid, bal)
            byte[] pairSignature = crypto.encrypt(this.username, initWid + String.valueOf(initBalance));

            String m = this.username + initWid + initBalance + Arrays.toString(pairSignature) + pubKey;

            byte[] signature = crypto.encrypt(this.username, m);

            OpenAccountRequest req = OpenAccountRequest.newBuilder()
                    .setUsername(this.username)
                    .setInitWid(initWid)
                    .setInitBalance(initBalance)
                    .setPairSignature(ByteString.copyFrom(pairSignature))
                    .setPublicKey(ByteString.copyFrom(encoded))
                    .setSignature(ByteString.copyFrom(signature))
                    .build();

            frontend.openAccount(req);

        } catch (StatusRuntimeException e) {
            return handleError(e);
        } catch (AccountAlreadyExistsException e) {
            return ANSI_RED + e.getMessage();
        }

        return ANSI_GREEN + "Account with name " +  this.username + " created";
    }

    public String send_amount(String receiverAccount, int amount) {
        try {
            if (amount < 0) throw new InvalidAmountException();

            CheckAccountResponse res = getCheckAccountResponse(this.username);

            this.rid++;

            long nonce = crypto.generateNonce();
            long timestamp = crypto.generateTimestamp();

            PublicKey senderKey = crypto.getPublicKey(this.username);
            PublicKey receiverKey = crypto.getPublicKey(receiverAccount);

            if (senderKey == null || receiverKey == null)
                throw new AccountDoesNotExistsException();

            int widToSend = res.getWid() + 1;
            int balanceToSend = res.getBalance() - amount;

            String transactionMessage = amount + this.username + receiverAccount + senderKey + receiverKey + widToSend;
            byte[] transactionSignature = crypto.encrypt(this.username, transactionMessage);

            Transaction transaction = Transaction.newBuilder()
                    .setAmount(amount)
                    .setSenderUsername(this.username)
                    .setReceiverUsername(receiverAccount)
                    .setSenderKey(ByteString.copyFrom(senderKey.getEncoded()))
                    .setReceiverKey(ByteString.copyFrom(receiverKey.getEncoded()))
                    .setWid(widToSend)
                    .setSignature(ByteString.copyFrom(transactionSignature))
                    .build();

            byte[] pairSignature = crypto.encrypt(this.username,  String.valueOf(widToSend) + balanceToSend);

            String m = transaction.toString() + nonce + timestamp + widToSend + balanceToSend + Arrays.toString(pairSignature);

            byte[] signature = crypto.encrypt(this.username, m);

            SendAmountRequest req = SendAmountRequest.newBuilder()
                    .setTransaction(transaction)
                    .setNonce(nonce)
                    .setTimestamp(timestamp)
                    .setBalance(balanceToSend)
                    .setPairSignature(ByteString.copyFrom(pairSignature))
                    .setSignature(ByteString.copyFrom(signature))
                    .build();

            frontend.sendAmount(req);

        } catch (StatusRuntimeException e) {
            return handleError(e);
        } catch (InvalidAmountException | AccountDoesNotExistsException e) {
            return ANSI_RED + e.getMessage();
        }
        return ANSI_GREEN + "Sent " + amount + " from " + this.username + " to " + receiverAccount;
    }

    public String check_account(String checkAccountName) {
        List<Transaction> pendingTransactions;
        int balance;

        try {
            CheckAccountResponse res = getCheckAccountResponse(checkAccountName);

            pendingTransactions = res.getPendingTransactionsList();
            balance = res.getBalance();

            this.rid++;

        } catch (StatusRuntimeException e) {
            return handleError(e);
        } catch (AccountDoesNotExistsException e) {
            return ANSI_RED + e.getMessage();
        }

        return ANSI_GREEN + "Account Status:\n\t" +
                "- Balance: " + balance +
                "\n\t- Pending transactions:" + pendingTransactions;
    }

    public String receive_amount() {
        int amountToReceive;
        try {
            CheckAccountResponse res = getCheckAccountResponse(this.username);

            this.rid++;

            PublicKey key = crypto.getPublicKey(this.username);

            amountToReceive = 0;
            List<Transaction> pendingTransactions = res.getPendingTransactionsList();

            if (pendingTransactions.isEmpty())
                return ANSI_GREEN + "No pending transactions.";

            int balance = res.getBalance();
            int wid = res.getWid();

            List<Transaction> transactions = new ArrayList<>();

            for (Transaction pending : pendingTransactions) {

                String transactionMessage =
                        pending.getAmount() + pending.getSenderUsername() + pending.getReceiverUsername()
                                + pending.getSenderKey() + pending.getReceiverKey() + wid;

                byte[] transactionSignature = crypto.encrypt(this.username, transactionMessage);

                System.out.println(Arrays.toString(transactionSignature)); // todo fix me

                transactions.add(
                        Transaction.newBuilder()
                                .setAmount(pending.getAmount())
                                .setSenderUsername(pending.getSenderUsername())
                                .setReceiverUsername(pending.getReceiverUsername())
                                .setSenderKey(pending.getSenderKey())
                                .setReceiverKey(pending.getReceiverKey())
                                .setWid(wid)
                                .setSignature(ByteString.copyFrom(transactionSignature))
                                .build()
                );
                wid++;
            }

            for (Transaction pendingTransaction : pendingTransactions)
                amountToReceive += pendingTransaction.getAmount();

            long nonce = crypto.generateNonce();
            long timestamp = crypto.generateTimestamp();

            int balanceToSend = balance + amountToReceive;
            int widToSend = wid + 1;

            byte[] pairSignature = crypto.encrypt(this.username,  String.valueOf(widToSend) + balanceToSend);

            String messageToSign = transactions + key.toString() + nonce + timestamp + widToSend + balanceToSend+ Arrays.toString(pairSignature);

            ReceiveAmountRequest req = ReceiveAmountRequest.newBuilder()
                    .addAllPendingTransactions(transactions)
                    .setPublicKey(ByteString.copyFrom(key.getEncoded()))
                    .setNonce(nonce)
                    .setTimestamp(timestamp)
                    .setWid(widToSend)
                    .setBalance(balanceToSend)
                    .setPairSignature(ByteString.copyFrom(pairSignature))
                    .setSignature(ByteString.copyFrom(crypto.encrypt(this.username, messageToSign)))
                    .build();

            ReceiveAmountResponse response = frontend.receiveAmount(req);

        } catch (StatusRuntimeException e) {
            return handleError(e);
        } catch (AccountDoesNotExistsException e) {
            return ANSI_RED + e.getMessage();
        }

        return ANSI_GREEN + "Amount deposited to your account: " + amountToReceive;
    }

    public String audit(String checkAccountName) {
        List<Transaction> transactions;

        try {

            long nonce = crypto.generateNonce();
            long timestamp = crypto.generateTimestamp();

            PublicKey clientKey = crypto.getPublicKey(this.username);
            PublicKey auditKey = crypto.getPublicKey(checkAccountName);

            if (auditKey == null) throw new AccountDoesNotExistsException();

            AuditRequest req = AuditRequest.newBuilder()
                    .setClientKey(ByteString.copyFrom(clientKey.getEncoded()))
                    .setAuditKey(ByteString.copyFrom(auditKey.getEncoded()))
                    .setNonce(nonce)
                    .setTimestamp(timestamp)
                    .setRid(this.rid + 1)
                    .build();
            AuditResponse res = frontend.audit(req);

            transactions = res.getTransactionsList();

        } catch (StatusRuntimeException e) {
            return handleError(e);
        } catch (AccountDoesNotExistsException e) {
            return ANSI_RED + e.getMessage();
        }

        return ANSI_GREEN + "Total transfers: " + transactions;
    }

    private String handleError(StatusRuntimeException e) {
        if (e.getStatus().getDescription() != null && e.getStatus().getDescription().equals("io exception")) {
            return ANSI_RED + "Warn: Server not responding!";
        } else {
            return ANSI_RED + e.getStatus().getDescription();
        }
    }

    private CheckAccountResponse getCheckAccountResponse(String username) throws AccountDoesNotExistsException {
        long nonce = crypto.generateNonce();
        long timestamp = crypto.generateTimestamp();

        PublicKey clientKey = crypto.getPublicKey(this.username);
        PublicKey key = crypto.getPublicKey(username);

        if (key == null || clientKey == null) throw new AccountDoesNotExistsException();

        CheckAccountRequest checkReq = CheckAccountRequest.newBuilder()
                .setClientKey(ByteString.copyFrom(clientKey.getEncoded()))
                .setCheckKey(ByteString.copyFrom(key.getEncoded()))
                .setNonce(nonce)
                .setTimestamp(timestamp)
                .setRid(this.rid + 1)
                .build();

        return frontend.checkAccount(checkReq);
    }

    void close() {
        frontend.close();
    }
}