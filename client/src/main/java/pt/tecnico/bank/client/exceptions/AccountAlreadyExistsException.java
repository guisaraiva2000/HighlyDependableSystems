package pt.tecnico.bank.client.exceptions;

public class AccountAlreadyExistsException extends Exception {

    private static final long serialVersionUID = 202104021434L;

    public AccountAlreadyExistsException() {
    }

    @Override
    public String getMessage() {
        return "ERROR: Account already exists.";
    }
}
