package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;

public  class Transfer {

    private ByteString destination;
    private int amount;

    public Transfer(ByteString destination, int amount) {
        this.destination = destination;
        this.amount = amount;
    }

    public ByteString getDestination() {
        return destination;
    }

    public void setDestination(ByteString destination) {
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
        return "{" +
                "destination=" + destination.toStringUtf8() +
                ", amount=" + amount +
                '}';
    }
}
