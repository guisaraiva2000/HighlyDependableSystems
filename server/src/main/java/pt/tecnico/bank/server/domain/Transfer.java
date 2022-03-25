package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;

import java.util.Arrays;

public  class Transfer {

    private byte[] destination;
    private int amount;

    public Transfer(byte[] destination, int amount) {
        this.destination = destination;
        this.amount = amount;
    }

    public byte[] getDestination() {
        return destination;
    }

    public void setDestination(byte[] destination) {
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
                "destination=" + Arrays.toString(destination) +
                ", amount=" + amount +
                '}';
    }
}
