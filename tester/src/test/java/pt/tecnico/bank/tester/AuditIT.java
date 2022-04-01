package pt.tecnico.bank.tester;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.client.Client;
import pt.tecnico.bank.server.ServerFrontendServiceImpl;

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
        client.receive_amount("audit_account1");

        String res1 = client.audit("audit_account1");
        String res2 = client.audit("audit_account2");

        assertEquals(res1, "Total transfers:\n" +
                "        - Sent: 10");
        assertEquals(res2, "Total transfers:\n" +
                "        - Received: 10");
    }

    @Test
    public void AuditOKTest2() {
        client.open_account("audit_account1");
        client.open_account("audit_account2");
        client.send_amount("audit_account1", "audit_account2", 10);
        client.send_amount("audit_account2", "audit_account1", 30);

        String res1 = client.check_account("audit_account1");
        String res2 = client.check_account("audit_account2");

        client.receive_amount("audit_account1");
        client.receive_amount("audit_account2");

        String res3 = client.audit("audit_account1");
        String res4 = client.audit("audit_account2");

        assertEquals(res1, "Account Status:\n" +
                "        - Balance: 100\n" +
                "        - On hold amount to send: 10\n" +
                "        - Pending transfers:\n" +
                "                - To receive: 30");

        assertEquals(res2, "Account Status:\n" +
                "        - Balance: 100\n" +
                "        - On hold amount to send: 30\n" +
                "        - Pending transfers:\n" +
                "                - To receive: 10");

        assertEquals(res3, "Total transfers:\n" +
                "        - Received: 30\n" +
                "        - Sent: 10");

        assertEquals(res4, "Total transfers:\n" +
                "        - Sent: 30\n" +
                "        - Received: 10");
    }

    @Test
    public void AccountDoesNotExistsKOTest() {
        String res = client.audit("audit_account_not_exists");
        assertEquals(res, "ERROR: Account does not exist");
    }
}
