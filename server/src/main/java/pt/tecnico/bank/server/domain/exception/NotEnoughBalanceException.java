package pt.tecnico.bank.server.domain.exception;

public class NotEnoughBalanceException extends Exception {

    private static final long serialVersionUID = 202104021434L;

    public NotEnoughBalanceException() {
    }

    @Override
    public String getMessage() {
        return "ERROR: Not enough balance to perform this transfer.";
    }
}
