package pt.tecnico.bank.server.domain;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.Comparator;
import java.util.LinkedList;

public class User implements Serializable {

    private final PublicKey pubKey;
    private final String username;
    private int wid;
    //private int rid;
    private byte[] challenge;
    private byte[] pairSignature;
    private int balance;
    private LinkedList<Transfer> totalTransfers = new LinkedList<>();    // transfer = (key, amount)
    private LinkedList<Transfer> pendingTransfers = new LinkedList<>();
    private NonceManager nonceManager = new NonceManager();


    public User(PublicKey pubKey, String username, int wid, int balance, byte[] pairSignature) {
        this.pubKey = pubKey;
        this.username = username;
        this.wid = wid;
        this.balance = balance;
        this.pairSignature = pairSignature;
    }

    public PublicKey getPubKey() {
        return pubKey;
    }

    public String getUsername() {
        return username;
    }

    public int getBalance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public int getWid() {
        return wid;
    }

    public byte[] getPairSignature() {
        return pairSignature;
    }

    public void setPairSignature(byte[] pairSignature) {
        this.pairSignature = pairSignature;
    }

    public void setWid(int wid) {
        this.wid = wid;
    }

    public byte[] getChallenge() {
        return challenge;
    }

    public void setChallenge(byte[] challenge) {
        this.challenge = challenge;
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
                ", totalTransfers=" + totalTransfers +
                ", pendingTransfers=" + pendingTransfers +
                '}';
    }
}
