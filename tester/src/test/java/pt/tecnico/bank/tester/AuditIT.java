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

public class AuditIT {

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
    public void AuditOKTest() {
        client.open_account("audit_account1");
        client.open_account("audit_account2");
        client.send_amount("audit_account1", "audit_account2", 10);
        client.receive_amount("audit_account2");

        String res1 = client.audit("audit_account1");
        String res2 = client.audit("audit_account2");

        assertEquals(client.ANSI_GREEN + "Total transfers:\n" +
                "\t- Sent: 10", res1);
        assertEquals(client.ANSI_GREEN + "Total transfers:\n" +
                "\t- Received: 10", res2);
    }

    @Test
    public void AuditOKTest2() {
        client.open_account("audit_account1_2");
        client.open_account("audit_account2_2");
        client.send_amount("audit_account1_2", "audit_account2_2", 10);
        client.send_amount("audit_account2_2", "audit_account1_2", 30);

        String res1 = client.check_account("audit_account1_2");
        String res2 = client.check_account("audit_account2_2");

        client.receive_amount("audit_account1_2");
        client.receive_amount("audit_account2_2");

        String res3 = client.audit("audit_account1_2");
        String res4 = client.audit("audit_account2_2");

        assertEquals(client.ANSI_GREEN + "Account Status:\n" +
                "\t- Balance: 100\n" +
                "\t- On hold amount to send: 10\n" +
                "\t- Pending transfers:\n" +
                "\t\t- To receive: 30", res1);

        assertEquals(client.ANSI_GREEN + "Account Status:\n" +
                "\t- Balance: 100\n" +
                "\t- On hold amount to send: 30\n" +
                "\t- Pending transfers:\n" +
                "\t\t- To receive: 10", res2);

        assertEquals(client.ANSI_GREEN + "Total transfers:\n" +
                "\t- Received: 30\n" +
                "\t- Sent: 10", res3);

        assertEquals(client.ANSI_GREEN + "Total transfers:\n" +
                "\t- Sent: 30\n" +
                "\t- Received: 10", res4);
    }

    @Test
    public void AccountDoesNotExistsKOTest() {
        String res = client.audit("audit_account_not_exists");
        assertEquals(client.ANSI_RED + "ERROR: Account does not exist.", res);
    }

    @AfterAll
    public static void cleanup() {
        File dir = Paths.get(System.getProperty("user.dir") + "\\CERTIFICATES\\").toFile();
        for(File file: Objects.requireNonNull(dir.listFiles()))
            if (!file.isDirectory() && !file.getName().equals("server.cert"))
                file.delete();
    }
}
