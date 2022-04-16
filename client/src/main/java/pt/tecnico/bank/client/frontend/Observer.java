package pt.tecnico.bank.client.frontend;

import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;

public class Observer<R> implements StreamObserver<R> {

    final ResponseCollector resCollector;
    final CountDownLatch finishLatch;
    final String sName;

    public Observer(ResponseCollector resCollector, CountDownLatch fLatch, String sName) {
        this.resCollector = resCollector;
        this.finishLatch = fLatch;
        this.sName = sName;
    }

    @Override
    public void onNext(R r) {
        System.out.println("Received " + /*r.toString().replace('\n', ' ')+*/ "from server " + this.sName);
        if(this.resCollector != null) {
            this.resCollector.addResponse(sName, r);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        System.out.println("Received error: " + throwable);
        finishLatch.countDown();
    }

    @Override
    public void onCompleted() {
        System.out.println("Request completed from server " + this.sName);
        finishLatch.countDown();
    }
}
