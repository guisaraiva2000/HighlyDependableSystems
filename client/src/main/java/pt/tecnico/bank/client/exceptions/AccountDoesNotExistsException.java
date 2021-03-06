package pt.tecnico.bank.client.exceptions;

public class AccountDoesNotExistsException extends Exception {

    private static final long serialVersionUID = 202104021434L;

    public AccountDoesNotExistsException() {
    }

    @Override
    public String getMessage() {
        return "ERROR: Account does not exist.";
    }
}
