package pt.tecnico.bank.server.domain;

import java.security.PublicKey;

public  class Transfer {

    private PublicKey destination;
    private int amount;

    public Transfer(PublicKey destination, int amount) {
        this.destination = destination;
        this.amount = amount;
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
        return amount < 0 ? "- Sent: " + (-amount) : "- Received: " + amount;
    }
}
