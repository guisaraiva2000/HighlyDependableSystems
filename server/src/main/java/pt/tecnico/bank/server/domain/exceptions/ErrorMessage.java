package pt.tecnico.bank.server.domain.exceptions;

public enum ErrorMessage {

    ACCOUNT_ALREADY_EXISTS("ERROR: Account already exists."),
    ACCOUNT_DOES_NOT_EXIST("ERROR: Account does not exist."),
    INVALID_NONCE("ERROR: Invalid nonce."),
    INVALID_BALANCE("ERROR: Invalid balance."),
    INVALID_POW("ERROR: Invalid Proof of Work."),
    NOT_ENOUGH_BALANCE("ERROR: Not enough balance to perform this transaction."),
    SAME_ACCOUNT("ERROR: Cannot send money to your own account."),
    BYZANTINE_CLIENT("ERROR: We do not accept requests from byzantine clients."),
    INVALID_SIGNATURE("ERROR: Either message was altered or the signature is not correct.");

    public final String label;

    ErrorMessage(String label) {
        this.label = label;
    }
}
