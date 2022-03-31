package pt.tecnico.bank.server;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.server.grpc.Server.*;

import static io.grpc.Status.ALREADY_EXISTS;
import static org.junit.jupiter.api.Assertions.*;

public class SendAmountIT {

    private ServerFrontend frontend;

    @BeforeEach
    public void setUp() {
        frontend = new ServerFrontend();
    }

    @AfterEach
    public void tearDown() {
        frontend.getService().close();
        frontend = null;
    }

    @Test
    public void SendAmountOKTest() {
        OpenAccountRequest oareq = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFromUtf8("123456"))
                .setBalance(100).build();
        frontend.openAccount(oareq);

        OpenAccountRequest oareq2 = OpenAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("654321")).build();
        frontend.openAccount(oareq2);

        SendAmountRequest req = SendAmountRequest.newBuilder()
                .setSourceKey(ByteString.copyFromUtf8("123456"))
                .setDestinationKey(ByteString.copyFromUtf8("654321"))
                .setAmount(20).build();
        SendAmountResponse res = frontend.sendAmount(req);
        assertTrue(res.getAck());
    }

    /*@Test
    public void UserAlreadyExistsKOTest() {
        SendAmountRequest req = SendAmountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("12345")).build();
        assertEquals(
                ALREADY_EXISTS.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.openAccount(req))
                        .getStatus()
                        .getCode());
    }*/
}
