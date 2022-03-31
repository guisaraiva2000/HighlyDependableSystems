package pt.tecnico.bank.client;

import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.tester.ServerFrontendServiceImpl;

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

        ServerFrontendServiceImpl frontend;
        Client client;

        try {
            frontend = new ServerFrontendServiceImpl();
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
                    client = new Client(frontend, username, password);

                    System.out.print(ANSI_YELLOW + "> ");
                    System.out.flush();
                    input = sin.nextLine();

                    if (input.equals("") || input.charAt(0) == '#')
                        continue;

                    tokens = input.split(" ");

                    switch (tokens[0]) {
                        case "open":
                            if (tokens.length == 2) {
                                System.out.println(client.open_account(tokens[1]));
                            } else {
                                System.err.println(ANSI_RED + "ERROR: Usage: open %accountName%");
                            }
                            break;
                        case "send":
                            if (tokens.length == 4) {
                                System.out.println(client.send_amount(tokens[1], tokens[2],
                                        Integer.parseInt(tokens[3])));
                            } else {
                                System.err.println(ANSI_RED + "ERROR: Usage: send %sender_account% %receiver_account% %amount%");
                            }
                            break;
                        case "check":
                            if (tokens.length == 2) {
                                System.out.println(client.check_account(tokens[1]));
                            } else {
                                System.err.println(ANSI_RED + "ERROR: Usage: check %account_name%");
                            }
                            break;
                        case "receive":
                            if (tokens.length == 2) {
                                System.out.println(client.receive_amount(tokens[1]));
                            } else {
                                System.err.println(ANSI_RED + "ERROR: Usage: receive %account_name%");
                            }
                            break;
                        case "audit":
                            if (tokens.length == 2) {
                                System.out.println(client.audit(tokens[1]));
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
        System.out.println(ANSI_CYAN + "| open    %accountName%                                             |");
        System.out.println(ANSI_CYAN + "| send    %sender_account% %receiver_account% %amount%              |");
        System.out.println(ANSI_CYAN + "| check   %account_name% %client_account_name%                      |");
        System.out.println(ANSI_CYAN + "| receive %account_name%                                            |");
        System.out.println(ANSI_CYAN + "| audit   %account_name% %client_account_name%                      |");
        System.out.println(ANSI_CYAN + "|-------------------------------------------------------------------|");
    }
}