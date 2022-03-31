package pt.tecnico.bank.server.domain.exceptions;

public class SameAccountException extends Exception {
    private static final long serialVersionUID = 202104021434L;

    public SameAccountException() {
    }

    @Override
    public String getMessage() {
        return "ERROR: Cannot send money to your own account.";
    }
}
