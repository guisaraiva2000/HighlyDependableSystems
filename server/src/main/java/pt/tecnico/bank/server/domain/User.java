package pt.tecnico.bank.server.domain;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.LinkedList;

public class User implements Serializable {

    private final PublicKey pubKey;
    private int balance;
    private int pendentAmount = 0; // stores the non-received money
    private LinkedList<Transfer> totalTransfers = new LinkedList<>(); // transfer = (key, amount)
    private LinkedList<Transfer> pendingTransfers = new LinkedList<>();
    private NonceManager nonceManager = new NonceManager();


    public User(PublicKey pubKey, int balance) {
        this.pubKey = pubKey;
        this.balance = balance;
    }

    public PublicKey getPubKey() {
        return pubKey;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public int getPendentAmount() {
        return pendentAmount;
    }

    public void setPendingAmount(int pendingAmount) {
        this.pendentAmount = pendingAmount;
    }

    public LinkedList<Transfer> getTotalTransfers() {
        return totalTransfers;
    }

    public void setTotalTransfers(LinkedList<Transfer> totalTransfers) {
        this.totalTransfers = totalTransfers;
    }

    public LinkedList<Transfer> getPendingTransfers() {
        return pendingTransfers;
    }

    public void setPendingTransfers(LinkedList<Transfer> pendingTransfers) {
        this.pendingTransfers = pendingTransfers;
    }

    public NonceManager getNonceManager() {
        return nonceManager;
    }

    public void setNonceManager(NonceManager nonceManager) {
        this.nonceManager = nonceManager;
    }

    @Override
    public String toString() {
        return "User{" +
                "pubKey=" + pubKey +
                ", balance=" + balance +
                ", pendingAmount=" + pendentAmount +
                ", totalTransfers=" + totalTransfers +
                ", pendingTransfers=" + pendingTransfers +
                '}';
    }
}
