package pt.tecnico.bank.tester;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.client.Client;
import pt.tecnico.bank.server.ServerFrontendServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CheckAccountIT {

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
    public void CheckAccountOKTest() {
        client.open_account("check_account1");
        client.open_account("check_account2");
        client.send_amount("check_account1", "check_account2", 10);

        String res1 = client.check_account("check_account1");
        String res2 = client.check_account("check_account2");

        assertEquals(res1, "Account Status:\n" +
                "        - Balance: 100\n" +
                "        - On hold amount to send: 10\n" +
                "        - Pending transfers:");

        assertEquals(res2, "Account Status:\n" +
                "        - Balance: 100\n" +
                "        - On hold amount to send: 0\n" +
                "        - Pending transfers:\n" +
                "                - To receive: 10");
    }

    @Test
    public void AccountDoesNotExistsKOTest() {
        String res = client.check_account("check_account_not_exists");
        assertEquals(res, "ERROR: Account does not exist");
    }
}
