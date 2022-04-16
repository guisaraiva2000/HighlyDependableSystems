package pt.tecnico.bank.server.domain.exceptions;

import com.google.protobuf.ByteString;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import pt.tecnico.bank.server.grpc.Server.ErrorResponse;

public class ServerStatusRuntimeException extends StatusRuntimeException {

    public ServerStatusRuntimeException(Status status, String errorMsg, long nonce, byte[] signature) {
        super(status.withDescription(errorMsg), generateSignedMetadata(errorMsg, nonce, signature));
    }

    private static Metadata generateSignedMetadata(String errorMsg, long nonce, byte[] signature) {
        Metadata metadata = new Metadata();
        metadata.put(
                ProtoUtils.keyForProto(ErrorResponse.getDefaultInstance()),
                ErrorResponse.newBuilder()
                        .setErrorMsg(errorMsg)
                        .setNonce(nonce)
                        .setSignature(ByteString.copyFrom(signature))
                        .build());
        return metadata;

    }
}
