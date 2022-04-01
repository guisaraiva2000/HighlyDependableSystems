package pt.tecnico.bank.tester;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.client.Client;
import pt.tecnico.bank.server.ServerFrontendServiceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenAccountIT {

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
    public void OpenAccountOKTest() {
        String res = client.open_account("test_account");
        assertEquals(res, "Account with name test_account created");
    }

    @Test
    public void AccountAlreadyExistsKOTest() {
        String res = client.open_account("test_account");
        assertEquals(res, "ERROR: Account already exists.");
    }

}
