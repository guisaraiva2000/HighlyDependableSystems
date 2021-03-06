package pt.tecnico.bank.server;

import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.server.domain.ServerBackend;
import pt.tecnico.bank.server.domain.exceptions.*;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;

import static io.grpc.Status.*;


public class ServerServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {

    private final ServerBackend serverBackend;

    public ServerServiceImpl(ServerBackend serverBackend) {
        this.serverBackend = serverBackend;
    }


    @Override
    public void openAccount(OpenAccountRequest request, StreamObserver<OpenAccountResponse> responseObserver) {
        try {

            responseObserver.onNext(
                    serverBackend.openAccount(
                            request.getUsername(),
                            request.getInitWid(),
                            request.getInitBalance(),
                            request.getPairSignature(),
                            request.getPublicKey(),
                            request.getSignature()
                    )
            );

            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public synchronized void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {
        try {

            responseObserver.onNext(
                    serverBackend.sendAmount(
                            request.getTransaction(),
                            request.getNonce(),
                            request.getTimestamp(),
                            request.getBalance(),
                            request.getPairSignature(),
                            request.getSignature()
                    )
            );

            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {
        try {

            responseObserver.onNext(
                    serverBackend.checkAccount(
                            request.getClientKey(),
                            request.getCheckKey(),
                            request.getNonce(),
                            request.getTimestamp(),
                            request.getRid(),
                            request.getSignature()
                    )
            );

            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public synchronized void receiveAmount(ReceiveAmountRequest request, StreamObserver<ReceiveAmountResponse> responseObserver) {
        try {

            responseObserver.onNext(
                    serverBackend.receiveAmount(
                            request.getPendingTransactionsList(),
                            request.getPublicKey(),
                            request.getNonce(),
                            request.getTimestamp(),
                            request.getWid(),
                            request.getBalance(),
                            request.getPairSignature(),
                            request.getSignature()
                    )
            );

            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
        try {

            responseObserver.onNext(
                    serverBackend.audit(
                            request.getClientKey(),
                            request.getAuditKey(),
                            request.getNonce(),
                            request.getTimestamp(),
                            request.getPowsMap(),
                            request.getRid(),
                            request.getSignature()
                    )
            );
            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public void checkAccountWriteBack(CheckAccountWriteBackRequest request, StreamObserver<CheckAccountWriteBackResponse> responseObserver) {
        try {

            responseObserver.onNext(
                    serverBackend.checkAccountWriteBack(
                            request.getClientKey(),
                            request.getCheckKey(),
                            request.getNonce(),
                            request.getTimestamp(),
                            request.getPendingTransactionsList(),
                            request.getBalance(),
                            request.getWid(),
                            request.getPairSign(),
                            request.getSignature()
                    )
            );
            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public void auditWriteBack(AuditWriteBackRequest request, StreamObserver<AuditWriteBackResponse> responseObserver) {
        try {

            responseObserver.onNext(
                    serverBackend.auditWriteBack(
                            request.getClientKey(),
                            request.getAuditKey(),
                            request.getNonce(),
                            request.getTimestamp(),
                            request.getTransactionsList(),
                            request.getSignature()
                    )
            );
            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public void pow(ProofOfWorkRequest request, StreamObserver<ProofOfWorkResponse> responseObserver) {
        try {

            responseObserver.onNext(
                    serverBackend.generateProofOfWork(
                            request.getPublicKey(),
                            request.getNonce(),
                            request.getTimestamp(),
                            request.getSignature()
                    )
            );
            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }

    @Override
    public void getRid(RidRequest request, StreamObserver<RidResponse> responseObserver) {
        try {

            responseObserver.onNext(
                    serverBackend.getRid(
                            request.getPublicKey(),
                            request.getNonce(),
                            request.getTimestamp(),
                            request.getSignature()
                    )
            );
            responseObserver.onCompleted();

        } catch (ServerStatusRuntimeException e) {
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException(e.getTrailers()));
        }
    }
}