package pt.tecnico.bank.server.domain.adeb;

import java.io.Serializable;
import java.security.PublicKey;

public class MyAdebProof implements Serializable {

    private final PublicKey serverKey;
    private final String message;
    private final byte[] signature;
    private final int wid;


    public MyAdebProof(PublicKey serverKey, String message, int wid, byte[] signature) {
        this.serverKey = serverKey;
        this.message = message;
        this.wid = wid;
        this.signature = signature;
    }

    public PublicKey getServerKey() {
        return serverKey;
    }

    public String getMessage() {
        return message;
    }

    public byte[] getSignature() {
        return signature;
    }

    public int getWid() {
        return wid;
    }
}

