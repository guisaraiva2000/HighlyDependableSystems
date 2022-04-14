package pt.tecnico.bank.crypto.exceptions;

public class UnauthorizedOperationException extends Exception {

    private static final long serialVersionUID = 202104021434L;

    public UnauthorizedOperationException() {
    }

    @Override
    public String getMessage() {
        return "ERROR: You do not have permission to perform this operation.";
    }
}
