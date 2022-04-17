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
        if(r instanceof EchoResponse) {
            receiveEchoResponses((EchoResponse) r);
        } else if (r instanceof ReadyResponse) {
            receiveReadyResponses((ReadyResponse) r);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        //System.out.println("Received error: " + throwable);
        finishLatch.countDown();
    }

    @Override
    public void onCompleted() {
    }


    private void receiveEchoResponses(EchoResponse res) {

        Key pubKey = crypto.bytesToKey(res.getKey());
        byte[] newSignature = crypto.byteStringToByteArray(res.getSignature());
        long echoNonce = res.getNonce();

        validateResponse(pubKey, newSignature, echoNonce);
    }

    private void receiveReadyResponses(ReadyResponse res) {

        Key pubKey = crypto.bytesToKey(res.getKey());
        byte[] newSignature = crypto.byteStringToByteArray(res.getSignature());
        long readyNonce = res.getNonce();

        validateResponse(pubKey, newSignature, readyNonce);
    }

    private void validateResponse(Key pubKey,byte[] newSignature, long echoNonce) {
        String newMessage = pubKey.toString() + echoNonce;

        if (crypto.validateMessage(crypto.getPublicKey(sName), newMessage, newSignature) && nonce + 1 == echoNonce)
            finishLatch.countDown();
    }


}
