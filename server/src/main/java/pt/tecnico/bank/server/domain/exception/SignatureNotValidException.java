package pt.tecnico.bank.server.domain.exception;

public class SignatureNotValidException extends Exception{
    private static final long serialVersionUID = 202104021434L;

    public SignatureNotValidException() {
    }

    @Override
    public String getMessage() {
        return "ERROR: Either message was altered or the signature is not correct.";
    }
}
