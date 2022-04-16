package pt.tecnico.bank.server.domain.exceptions;

public enum ErrorMessage {

    ACCOUNT_ALREADY_EXISTS("ERROR: Account already exists."),
    ACCOUNT_DOES_NOT_EXIST("ERROR: Account does not exist."),
    INVALID_NONCE("ERROR: Invalid nonce."),
    NOT_ENOUGH_BALANCE("ERROR: Not enough balance to perform this transfer."),
    SAME_ACCOUNT("ERROR: Cannot send money to your own account."),
    INVALID_SIGNATURE("ERROR: Either message was altered or the signature is not correct.");

    public final String label;

    ErrorMessage(String label) {
        this.label = label;
    }
}
