package pt.tecnico.bank.server.domain;

import pt.tecnico.bank.server.domain.adeb.MyAdebProof;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class User implements Serializable {

    private final PublicKey pubKey;
    private final String username;
    private int wid;
    private int rid;
    private byte[] challenge;
    private byte[] pairSignature;
    private int balance;
    private List<MyTransaction> totalTransfers = Collections.synchronizedList(new ArrayList<>());    // transfer = (key, amount)
    private List<MyTransaction> pendingMyTransactions = Collections.synchronizedList(new ArrayList<>());
    private List<MyAdebProof> adebProofs = Collections.synchronizedList(new ArrayList<>());
    private final NonceManager nonceManager = new NonceManager();


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

    public void setWid(int wid) {
        this.wid = wid;
    }

    public int getRid() {
        return rid;
    }

    public void setRid(int rid) {
        this.rid = rid;
    }

    public byte[] getPairSignature() {
        return pairSignature;
    }

    public void setPairSignature(byte[] pairSignature) {
        this.pairSignature = pairSignature;
    }

    public byte[] getChallenge() {
        return challenge;
    }

    public void setChallenge(byte[] challenge) {
        this.challenge = challenge;
    }

    public List<MyTransaction> getTotalTransfers() {
        return totalTransfers;
    }

    public void setTotalTransfers(List<MyTransaction> totalTransfers) {
        this.totalTransfers = totalTransfers;
    }

    public List<MyTransaction> getPendingMyTransactions() {
        return pendingMyTransactions;
    }

    public void setPendingMyTransactions(List<MyTransaction> pendingMyTransactions) {
        this.pendingMyTransactions = pendingMyTransactions;
    }

    public List<MyAdebProof> getAdebProofs() {
        return adebProofs;
    }

    public void setAdebProofs(List<MyAdebProof> adebProofs) {
        this.adebProofs = adebProofs;
    }

    public List<MyTransaction> getPendingTransfers() {
        return pendingMyTransactions;
    }

    public void setPendingTransfers(List<MyTransaction> pendingMyTransactions) {
        this.pendingMyTransactions = pendingMyTransactions;
    }

    public NonceManager getNonceManager() {
        return nonceManager;
    }

    @Override
    public String toString() {
        return "User{" +
                "pubKey=" + pubKey +
                ", balance=" + balance +
                ", totalTransfers=" + totalTransfers +
                ", pendingTransfers=" + pendingMyTransactions +
                '}';
    }
}
