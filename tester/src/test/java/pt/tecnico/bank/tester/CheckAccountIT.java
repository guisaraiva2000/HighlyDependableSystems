package pt.tecnico.bank.tester;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.client.Client;
import pt.tecnico.bank.server.ServerFrontend;

import java.io.File;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CheckAccountIT {

    private ServerFrontend frontend;
    private Client client;

    @BeforeEach
    public void setUp() {
        frontend = new ServerFrontend();
        client = new Client(frontend, "user_tester", "test");
    }

    @AfterEach
    public void tearDown() {
        frontend.close();
        frontend = null;
        client = null;
    }

    @Test
    public void CheckAccountOKTest() {
        client.open_account("check_account1");
        client.open_account("check_account2");
        client.send_amount("check_account1", "check_account2", 10);

        String res1 = client.check_account("check_account1");
        String res2 = client.check_account("check_account2");

        assertEquals(client.ANSI_GREEN + "Account Status:\n" +
                "\t- Balance: 100\n" +
                "\t- On hold amount to send: 10\n" +
                "\t- Pending transfers:", res1);

        assertEquals(client.ANSI_GREEN + "Account Status:\n" +
                "\t- Balance: 100\n" +
                "\t- On hold amount to send: 0\n" +
                "\t- Pending transfers:\n" +
                "\t\t- To receive: 10", res2);
    }

    @Test
    public void AccountDoesNotExistsKOTest() {
        String res = client.check_account("check_account_not_exists");
        assertEquals(client.ANSI_RED + "ERROR: Account does not exist.", res);
    }

    @AfterAll
    public static void cleanup() {
        File dir = Paths.get(System.getProperty("user.dir") + File.separator + "CERTIFICATES" + File.separator).toFile();
        for(File file: Objects.requireNonNull(dir.listFiles()))
            if (!file.isDirectory() && !file.getName().equals("server.cert"))
                file.delete();
    }
}
