package pt.tecnico.bank.tester;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.tester.domain.ServerFrontend;
import pt.tecnico.bank.server.grpc.Server.*;


public class ServerFrontendServiceImpl {

    private final ServerFrontend service;

    /**
     * Creates a frontend that contacts the only replica.
     */
    public ServerFrontendServiceImpl() {
        this.service = new ServerFrontend();
    }

    public ServerFrontend getService() {
        return service;
    }

    /* ---------- Services ---------- */

    public PingResponse ping(PingRequest request) {
        try {
            return this.service.getPingResponse(request);
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
            return this.service.getPingResponse(request);
        }
    }

    public OpenAccountResponse openAccount(OpenAccountRequest request) {
        try {
            return this.service.getOpenAccountResponse(request);
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
            return this.service.getOpenAccountResponse(request);
        }
    }

    public SendAmountResponse sendAmount(SendAmountRequest request) {
        try {
            return this.service.getSendAmountResponse(request);
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
            return this.service.getSendAmountResponse(request);
        }
    }

    public CheckAccountResponse checkAccount(CheckAccountRequest request) {
        CheckAccountResponse res = null;
        while (res == null) {
            try {
                res = this.service.getCheckAccountResponse(request);
            } catch (StatusRuntimeException sre) {
                exceptionHandler(sre);
            }
        }
        return res;
    }

    public ReceiveAmountResponse receiveAmount(ReceiveAmountRequest request) {
        try {
            return this.service.getReceiveAmountResponse(request);
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
            return this.service.getReceiveAmountResponse(request);
        }
    }

    public AuditResponse auditResponse(AuditRequest request) {
        try {
            return this.service.getAuditResponse(request);
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
            return this.service.getAuditResponse(request);
        }
    }


    // aux
    private void exceptionHandler(StatusRuntimeException sre) {
        if (sre.getStatus().getCode() != Status.DEADLINE_EXCEEDED.getCode())
            throw sre;
        System.out.println("Request dropped.");
        System.out.println("Resending...");
    }

}
