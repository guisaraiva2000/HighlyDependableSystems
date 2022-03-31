package pt.tecnico.bank.server.domain.exceptions;

public class NonceAlreadyUsedException extends Exception {
    private static final long serialVersionUID = 202104021434L;

    public NonceAlreadyUsedException() {
    }

    @Override
    public String getMessage() {
        return "ERROR: Nonce already used.";
    }
}
