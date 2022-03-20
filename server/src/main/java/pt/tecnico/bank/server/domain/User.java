package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;

import java.util.HashMap;
import java.util.LinkedHashMap;

public class User {

    private final ByteString pubKey;
    private int balance;
    private LinkedHashMap<ByteString, Integer> totalTransfers = new LinkedHashMap<>(); // transfer = (key, amount)
    private HashMap<ByteString, Integer> pendingTransfers = new HashMap<>();


    public User(ByteString pubKey, int balance) {
        this.pubKey = pubKey;
        this.balance = balance;
    }

    public ByteString getPubKey() {
        return pubKey;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public LinkedHashMap<ByteString, Integer> getTotalTransfers() {
        return totalTransfers;
    }

    public void setTotalTransfers(LinkedHashMap<ByteString, Integer> totalTransfers) {
        this.totalTransfers = totalTransfers;
    }

    public HashMap<ByteString, Integer> getPendingTransfers() {
        return pendingTransfers;
    }

    public void setPendingTransfers(HashMap<ByteString, Integer> pendingTransfers) {
        this.pendingTransfers = pendingTransfers;
    }

    @Override
    public String toString() {
        return "User{" +
                "pubKey=" + pubKey +
                ", balance=" + balance +
                '}';
    }
}
