package pt.tecnico.bank.server.domain.exception;

public class TimestampExpiredException extends Exception {
    private static final long serialVersionUID = 202104021434L;

    public TimestampExpiredException() {
    }

    @Override
    public String getMessage() {
        return "ERROR: Expired timestamp.";
    }
}
