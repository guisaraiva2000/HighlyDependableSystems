package pt.tecnico.bank.server;

import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;

public class ServerObserver<R> implements StreamObserver<R> {

    final ResponseCollector resCollector;
    final CountDownLatch finishLatch;
    final String replica;

    public ServerObserver(ResponseCollector resCollector, CountDownLatch fLatch, String replica) {
        this.resCollector = resCollector;
        this.finishLatch = fLatch;
        this.replica = replica;
    }

    @Override
    public void onNext(R r) {
        //System.out.println("Received " + r.toString().replace('\n', ' ')+ "from server " + this.replica);
        if(this.resCollector != null) {
            this.resCollector.addResponse(r);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        //System.out.println("Received error: " + throwable);
        finishLatch.countDown();
    }

    @Override
    public void onCompleted() {
        //System.out.println("Request completed from server " + this.replica);
        finishLatch.countDown();
    }
}
