package pt.tecnico.bank.server;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.server.domain.Server;
import pt.tecnico.bank.server.domain.exception.*;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;

import static io.grpc.Status.*;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.google.protobuf.ByteString;


public class ServerServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {

    private final Server server;

    public ServerServiceImpl() {
        this.server = new Server();
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        String input = request.getInput();

        if (input.isBlank()) {
            responseObserver.onError(INVALID_ARGUMENT.withDescription("Input cannot be empty!").asRuntimeException());
            return;
        }

        String output = "OK: " + input;
        PingResponse response = PingResponse.newBuilder().setOutput(output).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void openAccount(OpenAccountRequest request, StreamObserver<OpenAccountResponse> responseObserver) {
        try {
            boolean ack = server.openAccount(request.getPublicKey(), request.getBalance());

            
            OpenAccountResponse response = OpenAccountResponse.newBuilder().setAck(ack)
                                                                            .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountAlreadyExistsException e) {
            responseObserver.onError(ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {
        try {
            String[] r = server.sendAmount(request.getSourceKey(), request.getDestinationKey(), request.getAmount(), request.getNonce(), request.getTimestamp(), request.getSignature());
           
            boolean ack = Objects.equals(r[0], "true");
            long nonce = Long.parseLong(r[1]);
            long recvTimestamp = Long.parseLong(r[2]);
            long newTimestamp = Long.parseLong(r[3]);
            byte[] signature = r[4].getBytes(StandardCharsets.UTF_8);

            try(FileOutputStream fos = new FileOutputStream(System.getProperty("user.dir") + "\\m.txt")){
                fos.write(signature);
            } catch (IOException e) {
                e.printStackTrace();
            }

            SendAmountResponse response = SendAmountResponse.newBuilder()
                                                            .setAck(ack)
                                                            .setNonce(nonce)
                                                            .setRecvTimestamp(recvTimestamp)
                                                            .setNewTimestamp(newTimestamp)
                                                            .setSignature(ByteString.copyFrom(signature))
                                                            .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountDoesNotExistsException e) {
            responseObserver.onError(UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
        } catch (SameAccountException e) {
            responseObserver.onError(FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (NotEnoughBalanceException e) {
            responseObserver.onError(OUT_OF_RANGE.withDescription(e.getMessage()).asRuntimeException());
        } catch (NonceAlreadyUsedException e) {
            responseObserver.onError(FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (TimestampExpiredException e) {
            responseObserver.onError(FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (SignatureNotValidException e) {
            responseObserver.onError(FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } 
    }

    @Override
    public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {
        try {
            String[] r = server.checkAccount(request.getPublicKey());
            byte[] signature = r[2].getBytes(StandardCharsets.UTF_8);

            CheckAccountResponse response = CheckAccountResponse.newBuilder()
                                                                .setBalance(Integer.parseInt(r[0]))
                                                                .setPendentTransfers(r[1])
                                                                .setSignature(ByteString.copyFrom(signature))
                                                                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountDoesNotExistsException e) {
            responseObserver.onError(UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void receiveAmount(ReceiveAmountRequest request, StreamObserver<ReceiveAmountResponse> responseObserver) {
        try {

            String[] r = server.receiveAmount(request.getPublicKey(), request.getSignature(), request.getNonce(), request.getTimestamp());

            boolean ack = Objects.equals(r[0], "true");
            long nonce = Long.parseLong(r[1]);
            long recvTimestamp = Long.parseLong(r[2]);
            long newTimestamp = Long.parseLong(r[3]);
            byte[] signature = r[4].getBytes(StandardCharsets.UTF_8);

            ReceiveAmountResponse response = ReceiveAmountResponse.newBuilder()
                                                                    .setAck(ack)
                                                                    .setNonce(nonce)
                                                                    .setRecvTimestamp(recvTimestamp)
                                                                    .setNewTimestamp(newTimestamp)
                                                                    .setSignature(ByteString.copyFrom(signature))
                                                                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountDoesNotExistsException e) {
            responseObserver.onError(UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
        } catch (SignatureNotValidException e) {
            responseObserver.onError(FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (NonceAlreadyUsedException e) {
            responseObserver.onError(FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (TimestampExpiredException e) {
            responseObserver.onError(FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
        try {
            String[] r = server.audit(request.getPublicKey());
            byte[] signature = r[1].getBytes(StandardCharsets.UTF_8);

            AuditResponse response = AuditResponse.newBuilder().setTransferHistory(r[0])
                                                                .setSignature(ByteString.copyFrom(signature))
                                                                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountDoesNotExistsException e) {
            responseObserver.onError(UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
        }
    }

}