package pt.tecnico.bank.client.exceptions;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public class DefaultErrorException extends StatusRuntimeException {

    private static final long serialVersionUID = 202104021434L;

    public DefaultErrorException() {
        super(Status.ABORTED.withDescription("ERROR: Something went wrong! Try again later :("));
    }

}

