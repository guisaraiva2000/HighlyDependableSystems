package pt.tecnico.bank.tester;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.notification.RunListener;
import pt.tecnico.bank.client.Client;
import pt.tecnico.bank.server.ServerFrontendServiceImpl;

import java.io.File;
import java.nio.file.Paths;
import java.sql.Time;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SendAmountIT extends RunListener {

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
        assertEquals(client.ANSI_GREEN + "Sent 10 from send_account1 to send_account2", res);
    }

    @Test
    public void NotEnoughBalanceKOTest() {
        String res = client.send_amount("send_account1", "send_account2", 91);
        assertEquals(client.ANSI_RED + "ERROR: Not enough balance to perform this transfer.", res);
    }

    @AfterAll
    public static void cleanup() {
        File dir = Paths.get(System.getProperty("user.dir") , "CERTIFICATES").toFile();
        for(File file: Objects.requireNonNull(dir.listFiles()))
            if (!file.isDirectory() && !file.getName().equals("server.cert"))
                file.delete();
    }
}
