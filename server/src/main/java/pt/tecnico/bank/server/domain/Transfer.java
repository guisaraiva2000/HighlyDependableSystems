package pt.tecnico.bank.server.domain;

import java.security.PublicKey;

public  class Transfer {

    private PublicKey destination;
    private int amount;
    private final boolean isPending;

    public Transfer(PublicKey destination, int amount, boolean isPending) {
        this.destination = destination;
        this.amount = amount;
        this.isPending = isPending;
    }

    public PublicKey getDestination() {
        return destination;
    }

    public void setDestination(PublicKey destination) {
        this.destination = destination;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        if (isPending)
            return amount < 0 ? "- To send: " + (-amount) : "- To receive: " + amount;

        return amount < 0 ? "- Sent: " + (-amount) : "- Received: " + amount;
    }
}
