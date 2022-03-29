package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.ServerFrontend;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class ClientMain {
    
    private final static String USER_PATH = System.getProperty("user.dir") + "\\users.txt";
    private static String username = "";
    public static void main(String[] args) {
        
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
        boolean loggedIn = false;

        
        try {
            while(!loggedIn){
                System.out.println("Login to use bank application");
                System.out.print("Username: ");
                System.out.flush();
                input = sin.nextLine();

                String password = "";

                BufferedReader reader = new BufferedReader(new FileReader(USER_PATH));
                String line = reader.readLine();
                while (line != null) {
                    String[] data = line.split(":");
                    if(data[0].equals(input)){
                        username = data[0];
                        password = data[1];
                        break;
                    }
                    line = reader.readLine();
                }
                reader.close();

                System.out.print("Password: ");
                System.out.flush();
                input = sin.nextLine();

                if(password.equals(input))
                    loggedIn = true;
            
                while(loggedIn){
                    System.out.print("> ");
                    System.out.flush();
                    input = sin.nextLine();

                    if (input.equals("") || input.charAt(0) == '#')
                        continue;

                    tokens = input.split(" ");

                    switch (tokens[0]) {
                        case "open":
                            if (tokens.length == 4) {
                                try {
                                    client.open_account(tokens[1], Integer.parseInt(tokens[2]), tokens[3]);
                                } catch(Exception e){
                                    e.printStackTrace();
                                }
                            } else {
                                System.err.println("ERROR: Usage: open %accountName% %amount% %password%");
                            }
                            break;
                        case "send":
                            if (tokens.length == 5) {
                               /* try (FileOutputStream out = new FileOutputStream("test_client_send.txt")) {
                                    out.write(tokens[1].getBytes());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }*/

                                client.send_amount(tokens[1], tokens[2],
                                                    Integer.parseInt(tokens[3]),
                                                    tokens[4]);
                            } else {
                                System.err.println("ERROR: Usage: send %sender_account% %receiver_account% %amount% %password%");
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
                            if (tokens.length == 3) {
                                client.receive_amount(tokens[1], tokens[2]);
                            } else {
                                System.err.println("ERROR: Usage: receive %account_name% %password%");
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
                            loggedIn = false;
                            username = "";
                            break;
                        default:
                            System.err.println("ERROR: Command not recognized!");
                    }
                }
            }
        } catch (StatusRuntimeException e) {
            System.out.println("Caught exception with description: " + e.getStatus().getDescription());
        } catch (Exception e){
            e.printStackTrace();
        } finally {
            frontend.close();
            System.exit(0);
        }
    }

    public static void openAccount(String accountName, int amount, String password){
       /* try {
            client.open_account(accountName, amount, password);
        } catch(Exception e){
            e.printStackTrace();
        }*/
    }

    public static void sendAmount(){
        
    }

    public static void checkAccount(){
        
    }

    public static void receiveTransfers(){
        
    }

    public static void audit(){
        
    }

    public void logout(){
        
    }

    public static String getUsername(){
        return username;
    }
}