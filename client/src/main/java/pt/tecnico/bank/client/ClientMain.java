package pt.tecnico.bank.client;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.ServerFrontend;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

public class ClientMain {

    private final static String USER_PATH = System.getProperty("user.dir") + "\\CLIENTS\\users.txt";
    private static String username = "";
    private static String password = "";
    public static void main(String[] args) {

        System.out.println(ClientMain.class.getSimpleName());

        ServerFrontend frontend;
        Client client;

        try {
            frontend = new ServerFrontend();
        } catch (Exception e) {
            System.out.println("Caught exception with description: " + e.getMessage());
            return;
        }

        String ANSI_YELLOW = "\u001B[33m";
        String ANSI_RED = "\u001B[31m";
        String PURPLE = "\033[0;35m";

        Scanner sin = new Scanner(System.in);
        String input;
        String[] tokens;
        boolean loggedIn = false;

        try {
            while(true){
                System.out.println(PURPLE + "Login to use bank application or press ENTER to leave.");
                System.out.print("Username: ");
                System.out.flush();
                input = sin.nextLine();

                if (input.equals("")) break;

                readUserData(input);

                System.out.print("Password: ");
                System.out.flush();
                input = sin.nextLine();

                if(password.equals(input) && !input.equals("")) {
                    loggedIn = true;
                    displayCommands();
                }

                while(loggedIn){
                    client = new Client(frontend, username);

                    System.out.print(ANSI_YELLOW + "> ");
                    System.out.flush();
                    input = sin.nextLine();

                    if (input.equals("") || input.charAt(0) == '#')
                        continue;

                    tokens = input.split(" ");

                    switch (tokens[0]) {
                        case "open":
                            if (tokens.length == 4) {
                                client.open_account(tokens[1], Integer.parseInt(tokens[2]), tokens[3]);
                            } else {
                                System.err.println(ANSI_RED + "ERROR: Usage: open %accountName% %amount% %password%");
                            }
                            break;
                        case "send":
                            if (tokens.length == 5) {
                                client.send_amount(tokens[1], tokens[2],
                                        Integer.parseInt(tokens[3]), tokens[4]);
                            } else {
                                System.err.println(ANSI_RED + "ERROR: Usage: send %sender_account% %receiver_account% %amount% %password%");
                            }
                            break;
                        case "check":
                            if (tokens.length == 2) {
                                client.check_account(tokens[1]);
                            } else {
                                System.err.println(ANSI_RED + "ERROR: Usage: check %account_name%");
                            }
                            break;
                        case "receive":
                            if (tokens.length == 3) {
                                client.receive_amount(tokens[1], tokens[2]);
                            } else {
                                System.err.println(ANSI_RED + "ERROR: Usage: receive %account_name% %password%");
                            }
                            break;
                        case "audit":
                            if (tokens.length == 2) {
                                client.audit(tokens[1]);
                            } else {
                                System.err.println(ANSI_RED + "ERROR: Usage: audit %account_name%");
                            }
                            break;
                        case "quit":
                            loggedIn = false;
                            username = "";
                            break;
                        default:
                            System.err.println(ANSI_RED + "ERROR: Command not recognized!");
                    }
                }
            }

            sin.close();
        } catch (StatusRuntimeException e) {
            System.out.println("Caught exception with description: " + e.getStatus().getDescription());
        } catch (Exception e){
            e.printStackTrace();
        } finally {
            frontend.getService().close();
            System.exit(0);
        }
    }

    private static void readUserData(String input) throws IOException {
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
    }

    private static void displayCommands() {
        String ANSI_CYAN = "\u001B[36m";
        System.out.println(ANSI_CYAN + "|-------------------------- Bank Operations ------------------------|");
        System.out.println(ANSI_CYAN + "| open    %accountName% %amount% %password%                         |");
        System.out.println(ANSI_CYAN + "| send    %sender_account% %receiver_account% %amount% %password%   |");
        System.out.println(ANSI_CYAN + "| check   %account_name%                                            |");
        System.out.println(ANSI_CYAN + "| receive %account_name% %password%                                 |");
        System.out.println(ANSI_CYAN + "| audit   %account_name%                                            |");
        System.out.println(ANSI_CYAN + "|-------------------------------------------------------------------|");
    }
}