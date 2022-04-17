package pt.tecnico.bank.server.domain.adeb;

import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.crypto.Crypto;
import pt.tecnico.bank.server.grpc.Adeb.*;

import java.security.Key;
import java.util.concurrent.CountDownLatch;

public class AdebObserver<R> implements StreamObserver<R> {

    final CountDownLatch finishLatch;
    final String sName;
    final Crypto crypto;
    private final long nonce;


    public AdebObserver(CountDownLatch fLatch, String sName, Crypto crypto, long nonce) {
        this.finishLatch = fLatch;
        this.sName = sName;
        this.crypto = crypto;
        this.nonce = nonce;
    }

    @Override
    public void onNext(R r) {
        System.out.println("Received " + /*r.toString().replace('\n', ' ')+*/ "from server " + this.sName);

        if(r instanceof EchoResponse) {
            addEcho((EchoResponse) r);
        } else if (r instanceof ReadyResponse) {
            addReady((ReadyResponse) r);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        System.out.println("Received error: " + throwable);
        //finishLatch.countDown();
    }

    @Override
    public void onCompleted() {
        System.out.println("Request completed from server " + this.sName);
        //finishLatch.countDown();
    }


    private void addEcho(EchoResponse res) {

        Key pubKey = crypto.bytesToKey(res.getKey());
        boolean ack = res.getAck();
        byte[] newSignature = crypto.getSignature(res.getSignature());
        long echoNonce = res.getNonce();

        String newMessage = pubKey.toString() + ack + echoNonce;

        if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature) && nonce + 1 == echoNonce)
            if(ack)
                finishLatch.countDown();
    }

    private void addReady(ReadyResponse res) {

        Key pubKey = crypto.bytesToKey(res.getKey());
        boolean ack = res.getAck();
        byte[] newSignature = crypto.getSignature(res.getSignature());
        long echoNonce = res.getNonce();

        String newMessage = pubKey.toString() + ack + echoNonce;

        if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature) && nonce + 1 == echoNonce)
            if(ack)
                finishLatch.countDown();
    }


}
