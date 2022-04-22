package pt.tecnico.bank.server.domain;

import java.io.Serializable;
import java.security.PublicKey;

public class MyTransaction implements Serializable {

    private final int amount;
    private final String senderName;
    private final String receiverName;
    private final PublicKey senderKey;
    private final PublicKey receiverKey;
    private final int wid;
    private final byte[] signature;
    private final boolean sent;


    public MyTransaction(int amount, String senderName, String receiverName, PublicKey senderKey, PublicKey receiverKey, int wid, boolean sent, byte[] signature) {
        this.amount = amount;
        this.senderName = senderName;
        this.receiverName = receiverName;
        this.senderKey = senderKey;
        this.receiverKey = receiverKey;
        this.wid = wid;
        this.sent = sent;
        this.signature = signature;
    }

    public PublicKey getReceiverKey() {
        return receiverKey;
    }

    public int getAmount() {
        return amount;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public PublicKey getSenderKey() {
        return senderKey;
    }

    public int getWid() {
        return wid;
    }

    public byte[] getSignature() {
        return signature;
    }

    public boolean isSent() {
        return sent;
    }

}
