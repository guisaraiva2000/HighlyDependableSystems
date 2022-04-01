package pt.tecnico.bank.tester;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.client.Client;
import pt.tecnico.bank.server.ServerFrontendServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SendAmountIT {

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
    public void SendAmountOKTest() {
        client.open_account("send_account1");
        client.open_account("send_account2");
        String res = client.send_amount("send_account1", "send_account2", 10);
        assertEquals(res, "Sent 10 from send_account1 to send_account2");
    }

    @Test
    public void NotEnoughBalanceKOTest() {
        String res = client.send_amount("send_account1", "send_account2", 91);
        assertEquals(res, "ERROR: Not enough balance to perform this transfer.");
    }
}
