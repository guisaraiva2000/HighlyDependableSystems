package pt.tecnico.bank.client.exceptions;

public class InvalidAmountException extends Exception {

    private static final long serialVersionUID = 202104021434L;

    public InvalidAmountException() {
    }

    @Override
    public String getMessage() {
        return "ERROR: Invalid amount.";
    }
}
