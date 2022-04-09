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

public class OpenAccountIT {

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
    public void OpenAccountOKTest() {
        String res = client.open_account("test_account");
        assertEquals(client.ANSI_GREEN + "Account with name test_account created", res);
    }

    @Test
    public void AccountAlreadyExistsKOTest() {
        String res = client.open_account("test_account");
        assertEquals(client.ANSI_RED + "ERROR: Account already exists.", res);
    }

    @AfterAll
    public static void cleanup() {
        File dir = Paths.get(System.getProperty("user.dir") + File.separator + "CERTIFICATES" + File.separator).toFile();
        for(File file: Objects.requireNonNull(dir.listFiles()))
            if (!file.isDirectory() && !file.getName().equals("server.cert"))
                file.delete();
    }

}
