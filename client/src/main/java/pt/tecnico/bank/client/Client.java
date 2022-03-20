package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.grpc.Server.*;

public class Client {

    public Client(){
    }

    void open_account(ByteString key, int amount){
        try {
            OpenAccountRequest o = OpenAccountRequest.newBuilder().setPublicKey(key)
                                                                  .setBalance(amount).build();
            System.out.println("Account with key " + key + " created");
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    void send_amount(ByteString orig_key, ByteString dest_key, int amount){
        try {
            SendAmountRequest s = SendAmountRequest.newBuilder().setSourceKey(orig_key)
                                                                .setDestinationKey(dest_key)
                                                                .setAmount(amount).build();
            System.out.println("Send " + amount + " from " + orig_key + " to " + dest_key);
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    void check_account(ByteString key){
        try {
            CheckAccountRequest c = CheckAccountRequest.newBuilder().setPublicKey(key).build();
            //System.out.println(key + " " + frontend.check_account(c).getCheckAccount());
        } catch (StatusRuntimeException e) {
            printError(e);
        }
    }

    void receive_amount(ByteString key){
        try {
            ReceiveAmountRequest r = ReceiveAmountRequest.newBuilder().setPublicKey(key).build();
            System.out.println("Received from " + key);
        } catch (StatusRuntimeException e) {
            printError(e);
        }

    }
    
    void audit(ByteString key){
        try {
            
            AuditRequest a = AuditRequest.newBuilder().setPublicKey(key).build();
            
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