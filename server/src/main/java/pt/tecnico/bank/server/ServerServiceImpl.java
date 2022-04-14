package pt.tecnico.bank.server;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.server.domain.Server;
import pt.tecnico.bank.server.domain.exceptions.*;
import pt.tecnico.bank.server.grpc.Server.*;
import pt.tecnico.bank.server.grpc.ServerServiceGrpc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Objects;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import static io.grpc.Status.*;


public class ServerServiceImpl extends ServerServiceGrpc.ServerServiceImplBase {

    private final Server server;

    public ServerServiceImpl(String sName, int port, int nByzantineServers) {
        this.server = new Server(sName, port, nByzantineServers);
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
            String[] r = server.openAccount(request.getPublicKey(), request.getSignature());

            boolean ack = Objects.equals(r[0], "true");
            byte[] pubKey = r[1].getBytes(StandardCharsets.ISO_8859_1);
            byte[] signature = r[2].getBytes(StandardCharsets.ISO_8859_1);


            OpenAccountResponse response = OpenAccountResponse.newBuilder().setAck(ack)
                    .setPublicKey(ByteString.copyFrom(pubKey))
                    .setSignature(ByteString.copyFrom(signature))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (AccountAlreadyExistsException e) {
            responseObserver.onError(ALREADY_EXISTS.withDescription(e.getMessage()).asRuntimeException());
        } catch (InvalidKeySpecException | IOException | NoSuchAlgorithmException | UnrecoverableKeyException
                | CertificateException | KeyStoreException | SignatureException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | SignatureNotValidException e) {
            e.printStackTrace();
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void sendAmount(SendAmountRequest request, StreamObserver<SendAmountResponse> responseObserver) {
        try {
            String[] r = server.sendAmount(request.getSourceKey(), request.getDestinationKey(), request.getAmount(),
                    request.getNonce(), request.getTimestamp(), request.getSignature());

            boolean ack = Objects.equals(r[0], "true");

            long nonce = Long.parseLong(r[2]);
            long recvTimestamp = Long.parseLong(r[3]);
            long newTimestamp = Long.parseLong(r[4]);
            byte[] signature = r[5].getBytes(StandardCharsets.ISO_8859_1);

            SendAmountResponse response = SendAmountResponse.newBuilder()
                    .setAck(ack)
                    .setPublicKey(r[1])
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
        } catch (InvalidKeySpecException | IOException | NoSuchAlgorithmException | UnrecoverableKeyException |
                CertificateException | KeyStoreException | SignatureException | InvalidKeyException | SignatureNotValidException e) {
            e.printStackTrace();
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void checkAccount(CheckAccountRequest request, StreamObserver<CheckAccountResponse> responseObserver) {
        try {
            String[] r = server.checkAccount(request.getPublicKey());
            long newTimestamp = Long.parseLong(r[3]);
            byte[] signature = r[4].getBytes(StandardCharsets.ISO_8859_1);

            CheckAccountResponse response = CheckAccountResponse.newBuilder()
                    .setBalance(Integer.parseInt(r[0]))
                    .setPendentAmount(Integer.parseInt(r[1]))
                    .setPendentTransfers(r[2])
                    .setNewTimestamp(newTimestamp)
                    .setSignature(ByteString.copyFrom(signature))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountDoesNotExistsException e) {
            responseObserver.onError(UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException |
                KeyStoreException | IOException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void receiveAmount(ReceiveAmountRequest request, StreamObserver<ReceiveAmountResponse> responseObserver) {
        try {

            String[] r = server.receiveAmount(request.getPublicKey(), request.getSignature(), request.getNonce(), request.getTimestamp());

            int recvAmount = Integer.parseInt(r[0]);
            long nonce = Long.parseLong(r[2]);
            long recvTimestamp = Long.parseLong(r[3]);
            long newTimestamp = Long.parseLong(r[4]);
            byte[] signature = r[5].getBytes(StandardCharsets.ISO_8859_1);

            ReceiveAmountResponse response = ReceiveAmountResponse.newBuilder()
                    .setRecvAmount(recvAmount)
                    .setPublicKey(r[1])
                    .setNonce(nonce)
                    .setRecvTimestamp(recvTimestamp)
                    .setNewTimestamp(newTimestamp)
                    .setSignature(ByteString.copyFrom(signature))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountDoesNotExistsException e) {
            responseObserver.onError(UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
        } catch (NonceAlreadyUsedException e) {
            responseObserver.onError(FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (TimestampExpiredException e) {
            responseObserver.onError(FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (SignatureNotValidException | InvalidKeySpecException | NoSuchAlgorithmException | UnrecoverableKeyException |
                CertificateException | KeyStoreException | IOException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void audit(AuditRequest request, StreamObserver<AuditResponse> responseObserver) {
        try {
            String[] r = server.audit(request.getPublicKey());
            long newTimestamp = Long.parseLong(r[1]);
            byte[] signature = r[2].getBytes(StandardCharsets.ISO_8859_1);

            AuditResponse response = AuditResponse.newBuilder().setTransferHistory(r[0])
                    .setNewTimestamp(newTimestamp)
                    .setSignature(ByteString.copyFrom(signature))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (AccountDoesNotExistsException e) {
            responseObserver.onError(UNAVAILABLE.withDescription(e.getMessage()).asRuntimeException());
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | UnrecoverableKeyException | CertificateException
                | KeyStoreException | IOException | SignatureException | InvalidKeyException e) {
            e.printStackTrace();
            responseObserver.onError(INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

}