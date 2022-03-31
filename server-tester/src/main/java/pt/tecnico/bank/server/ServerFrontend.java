package pt.tecnico.bank.server;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bank.server.domain.FrontendService;
import pt.tecnico.bank.server.grpc.Server.*;


public class ServerFrontend {

    private final FrontendService service;

    /**
     * Creates a frontend that contacts the only replica.
     */
    public ServerFrontend() {
        this.service = new FrontendService();
    }

    public FrontendService getService() {
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
        try {
            return this.service.getCheckAccountResponse(request);
        } catch (StatusRuntimeException sre) {
            exceptionHandler(sre);
            return this.service.getCheckAccountResponse(request);
        }
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
