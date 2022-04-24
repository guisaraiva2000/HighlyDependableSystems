package pt.tecnico.bank.server.domain;

import pt.tecnico.bank.server.domain.adeb.MyAdebProof;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class User implements Serializable {

    private final PublicKey pubKey;
    private final String username;
    private int wid;
    private int rid;
    private byte[] pairSignature;
    private int balance;
    private List<MyTransaction> totalTransactions = Collections.synchronizedList(new ArrayList<>());    // transaction = (key, amount)
    private List<MyTransaction> pendingMyTransactions = Collections.synchronizedList(new ArrayList<>());
    private List<MyAdebProof> adebProofs = Collections.synchronizedList(new ArrayList<>());
    private final NonceManager nonceManager = new NonceManager();
    private ConcurrentHashMap<String, Long> challenges = new ConcurrentHashMap<>();


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

    public List<MyTransaction> getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(List<MyTransaction> totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public List<MyAdebProof> getAdebProofs() {
        return adebProofs;
    }

    public void setAdebProofs(List<MyAdebProof> adebProofs) {
        this.adebProofs = adebProofs;
    }

    public List<MyTransaction> getPendingTransactions() {
        return pendingMyTransactions;
    }

    public void setPendingTransactions(List<MyTransaction> pendingMyTransactions) {
        this.pendingMyTransactions = pendingMyTransactions;
    }

    public NonceManager getNonceManager() {
        return nonceManager;
    }

    public ConcurrentHashMap<String, Long> getChallenges() {
        return challenges;
    }

    public void addChallenge(String challenge) {
        this.challenges.put(challenge, System.currentTimeMillis() / 1000);
    }

    @Override
    public String toString() {
        return "User{" +
                "pubKey=" + pubKey +
                ", balance=" + balance +
                ", totalTransactions=" + totalTransactions +
                ", pendingTransactions=" + pendingMyTransactions +
                '}';
    }
}
