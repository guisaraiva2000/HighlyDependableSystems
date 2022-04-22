import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.*;
import pt.tecnico.bank.server.ServerFrontendServiceImpl;
import pt.tecnico.bank.server.grpc.Server;
import pt.tecnico.bank.server.grpc.Server.*;

import static io.grpc.Status.INVALID_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PingIT {

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
    public void PingHubOKTest() {
        PingRequest request = PingRequest.newBuilder().setInput("localhost:8080").build();
        Server.PingResponse response = frontend.ping(request);
        assertEquals("OK: localhost:8080", response.getOutput());
    }

    @Test
    public void InvalidInputKOTest() {
        PingRequest request = PingRequest.newBuilder().setInput("").build();
        assertEquals(
                INVALID_ARGUMENT.getCode(),
                assertThrows(
                        StatusRuntimeException.class, () -> frontend.ping(request))
                        .getStatus()
                        .getCode());
    }
}
