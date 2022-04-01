package pt.tecnico.bank.tester;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.client.Client;
import pt.tecnico.bank.server.ServerFrontendServiceImpl;

import java.io.File;
import java.nio.file.Paths;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReceiveAmountIT {

    private ServerFrontendServiceImpl frontend;
    private Client client;

    @BeforeEach
    public void setUp() {
        frontend = new ServerFrontendServiceImpl();
        client = new Client(frontend, "user_tester", "test");
    }

    @AfterEach
    public void tearDown() {
        frontend.getService().close();
        frontend = null;
        client = null;
    }

    @Test
    public void ReceiveAmountOKTest() {
        client.open_account("receive_account1");
        client.open_account("receive_account2");
        client.send_amount("receive_account1", "receive_account2", 10);

        String res = client.receive_amount("receive_account2");

        assertEquals(client.ANSI_GREEN + "Amount deposited to your account: 10", res);
    }

    @Test
    public void AccountDoesNotExistsKOTest() {
        String res = client.receive_amount("receive_account_not_exists");
        assertEquals(client.ANSI_RED + "ERROR: Account does not exist.", res);
    }

    @Test
    public void ManInTheMiddleKOTest() {
        client.open_account("receive_account1_2");
        client.open_account("receive_account2_2");
        client.send_amount("receive_account1_2", "receive_account2_2", 10);

        Client attacker = new Client(frontend, "attacker", "pass1");
        String res = attacker.receive_amount("receive_account2_2");

        assertEquals(client.ANSI_RED + "ERROR: You do not have permission to perform this operation.", res);
    }

    @AfterAll
    public static void cleanup() {
        File dir = Paths.get(System.getProperty("user.dir") + File.separator + "CERTIFICATES" + File.separator).toFile();
        for(File file: Objects.requireNonNull(dir.listFiles()))
            if (!file.isDirectory() && !file.getName().equals("server.cert"))
                file.delete();
    }
}
