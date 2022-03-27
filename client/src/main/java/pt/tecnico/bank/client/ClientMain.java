package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.ServerFrontend;

import java.util.Scanner;

public class ClientMain {

    public static void main(String[] args) {
        
        //final String help =
                
        System.out.println(ClientMain.class.getSimpleName());

        ServerFrontend frontend;
        Client client;

        try {
            frontend = new ServerFrontend();
            client = new Client(frontend);
        } catch (Exception e) {
            System.out.println("Caught exception with description: " + e.getMessage());
            return;
        }

        ByteString publicKey = ByteString.copyFromUtf8("public"); //TODO create key pair here ???

        Scanner sin = new Scanner(System.in);
        String input;
        String[] tokens;
        boolean close = false;

        boolean opened = false; // TODO just for testing, delete after we got the keys

        try {
            while (!close) {
                System.out.print("> ");
                System.out.flush();
                input = sin.nextLine();

                if (input.equals("") || input.charAt(0) == '#')
                    continue;

                tokens = input.split(" ");

                switch (tokens[0]) {
                    case "open":
                        if (tokens.length == 3 && !opened) {
                            opened = true; // TODO just for testing, delete after we got the keys
                            try{
                            client.open_account(Integer.parseInt(tokens[1]), tokens[2]);
                            } catch(Exception e){
                                e.printStackTrace();
                            }
                        } else {
                            System.err.println("ERROR: Usage: open %amount% %password%");
                        }
                        break;
                    case "send":
                        if (tokens.length == 5) {
                            client.send_amount(ByteString.copyFrom(tokens[1].getBytes()),
                                                ByteString.copyFrom(tokens[1].getBytes()),
                                                Integer.parseInt(tokens[3]),
                                                tokens[4]);
                        } else {
                            System.err.println("ERROR: Usage: send %orig_key% %dest_key% %amount% %password%");
                        }
                        break;
                    case "check":
                        if (tokens.length == 1) {
                            client.check_account(publicKey);
                        } else {
                            System.err.println("ERROR: Usage: check");
                        }
                        break;
                    case "receive":
                        if (tokens.length == 1) {
                            client.receive_amount(publicKey);
                        } else {
                            System.err.println("ERROR: Usage: receive");
                        }
                        break;
                    case "audit":
                        if (tokens.length == 1) {
                            client.audit(publicKey);
                        } else {
                            System.err.println("ERROR: Usage: audit");
                        }
                        break;
                    case "quit":
                        close = true;
                        break;
                    default:
                        System.err.println("ERROR: Command not recognized!");
                }
            }
        } catch (StatusRuntimeException e) {
            System.out.println("Caught exception with description: " + e.getStatus().getDescription());
        } finally {
            frontend.close();
            System.exit(0);
        }
    }
}