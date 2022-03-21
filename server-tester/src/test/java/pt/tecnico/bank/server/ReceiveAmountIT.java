package pt.tecnico.bank.server;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.server.grpc.Server.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReceiveAmountIT {

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
    public void ReceiveAmountOKTest() {
        OpenAccountRequest oareq = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFromUtf8("123456789"))
                .setBalance(100).build();
        frontend.openAccount(oareq);

        OpenAccountRequest oareq2 = OpenAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("987654321")).build();
        frontend.openAccount(oareq2);

        SendAmountRequest sareq = SendAmountRequest.newBuilder()
                .setSourceKey(ByteString.copyFromUtf8("123456789"))
                .setDestinationKey(ByteString.copyFromUtf8("987654321"))
                .setAmount(20).build();
        frontend.sendAmount(sareq);

        ReceiveAmountRequest req = ReceiveAmountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("987654321")).build();
        ReceiveAmountResponse res = frontend.receiveAmount(req);

        assertTrue(res.getAck());

        CheckAccountRequest careq = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("987654321")).build();
        CheckAccountResponse cares = frontend.checkAccount(careq);
        assertEquals(20, cares.getBalance());
        assertEquals("", cares.getPendentTransfers());

        CheckAccountRequest careq2 = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("123456789")).build();
        CheckAccountResponse cares2 = frontend.checkAccount(careq2);
        assertEquals(80, cares2.getBalance());

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
