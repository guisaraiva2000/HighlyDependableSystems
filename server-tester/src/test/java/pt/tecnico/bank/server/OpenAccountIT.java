package pt.tecnico.bank.server;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.server.grpc.Server.OpenAccountRequest;
import pt.tecnico.bank.server.grpc.Server.OpenAccountResponse;

import static io.grpc.Status.ALREADY_EXISTS;
import static org.junit.jupiter.api.Assertions.*;

public class OpenAccountIT {

    private ServerFrontend frontend;

    @BeforeEach
    public void setUp() {
        frontend = new ServerFrontend();
    }

    @AfterEach
    public void tearDown() {
        frontend.close();
        frontend = null;
    }

    @Test
    public void OpenAccountOKTest() {
        OpenAccountRequest req = OpenAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("12345")).build();
        OpenAccountResponse res = frontend.openAccount(req);
        assertTrue(res.getAck());
    }

    @Test
    public void UserAlreadyExistsKOTest() {
        OpenAccountRequest req = OpenAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("12345")).build();
        assertEquals(
                ALREADY_EXISTS.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.openAccount(req))
                        .getStatus()
                        .getCode());
    }

}
