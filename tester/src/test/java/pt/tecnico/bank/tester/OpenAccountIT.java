package pt.tecnico.bank.tester;

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

    private ServerFrontendServiceImpl frontend;

    @BeforeEach
    public void setUp() {
        frontend = new ServerFrontendServiceImpl();
    }

    @AfterEach
    public void tearDown() {
        frontend.getService().close();
        frontend = null;
    }

    @Test
    public void OpenAccountOKTest() {
        OpenAccountRequest req = OpenAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("openOK")).build();
        OpenAccountResponse res = frontend.openAccount(req);
        assertTrue(res.getAck());
    }

    @Test
    public void UserAlreadyExistsKOTest() {
        OpenAccountRequest req1 = OpenAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("openNOK")).build();
        frontend.openAccount(req1);

        OpenAccountRequest req2 = OpenAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("openNOK")).build();
        assertEquals(
                ALREADY_EXISTS.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.openAccount(req2))
                        .getStatus()
                        .getCode());
    }

}
