package pt.tecnico.bank.tester;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.tecnico.bank.server.grpc.Server.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuditIT {

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
    public void AuditOKTest() {
        OpenAccountRequest oareq = OpenAccountRequest.newBuilder()
                .setPublicKey(ByteString.copyFromUtf8("12345678"))
                .build();
        frontend.openAccount(oareq);

        OpenAccountRequest oareq2 = OpenAccountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("87654321")).build();
        frontend.openAccount(oareq2);

        SendAmountRequest sareq = SendAmountRequest.newBuilder()
                .setSourceKey(ByteString.copyFromUtf8("12345678"))
                .setDestinationKey(ByteString.copyFromUtf8("87654321"))
                .setAmount(20).build();
        frontend.sendAmount(sareq);

        ReceiveAmountRequest rareq = ReceiveAmountRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("87654321")).build();
        frontend.receiveAmount(rareq);

        AuditRequest req = AuditRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("12345678")).build();
        AuditResponse res = frontend.auditResponse(req);

        AuditRequest req2 = AuditRequest.newBuilder().setPublicKey(ByteString.copyFromUtf8("87654321")).build();
        AuditResponse res2 = frontend.auditResponse(req2);

        assertEquals("{destination=87654321, amount=-20}", res.getTransferHistory());
        assertEquals("{destination=12345678, amount=20}", res2.getTransferHistory());

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
