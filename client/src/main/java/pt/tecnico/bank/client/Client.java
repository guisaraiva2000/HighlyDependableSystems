package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.ServerFrontend;
import pt.tecnico.bank.server.grpc.Server.*;

public class Client {

    private final ServerFrontend frontend;

    public Client(ServerFrontend frontend){
        this.frontend = frontend;
    }

    void open_account(ByteString key, int amount){
        try {
            OpenAccountRequest req = OpenAccountRequest.newBuilder().setPublicKey(key)
                                                                  .setBalance(amount).build();
            frontend.openAccount(req);
            System.out.println("Account with key " + key + " created");
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    void send_amount(ByteString orig_key, ByteString dest_key, int amount){
        try {
            SendAmountRequest req = SendAmountRequest.newBuilder().setSourceKey(orig_key)
                                                                .setDestinationKey(dest_key)
                                                                .setAmount(amount).build();
            frontend.sendAmount(req);
            System.out.println("Send " + amount + " from " + orig_key + " to " + dest_key);
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    void check_account(ByteString key){
        try {
            CheckAccountRequest req = CheckAccountRequest.newBuilder().setPublicKey(key).build();
            CheckAccountResponse res = frontend.checkAccount(req);

            System.out.println("Account Status:\n" + "Balance: " + res.getBalance() +
                    "\nPending transfers: " + res.getPendentTransfers() );
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    void receive_amount(ByteString key){
        try {
            ReceiveAmountRequest req = ReceiveAmountRequest.newBuilder().setPublicKey(key).build();
            frontend.receiveAmount(req);
            System.out.println("Amount deposited to your account");
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }
    
    void audit(ByteString key){
        try {
            AuditRequest req = AuditRequest.newBuilder().setPublicKey(key).build();
            AuditResponse res = frontend.auditResponse(req);
            System.out.println("Total tranfers:\n" + res.getTransferHistory());
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    private void printError(StatusRuntimeException e) {
        if (e.getStatus().getDescription() != null && e.getStatus().getDescription().equals("io exception")) {
            System.out.println("Warn: Server not responding!");
        } else {
            System.out.println(e.getStatus().getDescription());
        }
    }

}