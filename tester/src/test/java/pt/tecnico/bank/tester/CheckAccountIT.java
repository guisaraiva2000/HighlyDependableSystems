package pt.tecnico.bank.tester;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.server.grpc.Server.*;

import static org.junit.jupiter.api.Assertions.*;

public class CheckAccountIT {

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
    public void CheckAccountOKTest() {
        OpenAccountRequest oareq = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFromUtf8("1234567"))
                .build();
        frontend.openAccount(oareq);

        OpenAccountRequest oareq2 = OpenAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("7654321")).build();
        frontend.openAccount(oareq2);

        SendAmountRequest sareq = SendAmountRequest.newBuilder()
                .setSourceKey(ByteString.copyFromUtf8("1234567"))
                .setDestinationKey(ByteString.copyFromUtf8("7654321"))
                .setAmount(20).build();
        frontend.sendAmount(sareq);

        CheckAccountRequest req = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("7654321")).build();
        CheckAccountResponse res = frontend.checkAccount(req);
        assertEquals(0, res.getBalance());
        assertEquals("{destination=1234567, amount=20}", res.getPendentTransfers());
    }

   /* @Test
    public void UserAlreadyExistsKOTest() {
        CheckAccountRequest req = CheckAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("12345")).build();
        assertEquals(
                ALREADY_EXISTS.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.openAccount(req))
                        .getStatus()
                        .getCode());
    }*/
}