package pt.tecnico.bank.server.domain.adeb;

import io.grpc.stub.StreamObserver;

public class AdebObserver<R> implements StreamObserver<R> {


    public AdebObserver() {
    }

    @Override
    public void onNext(R r) {
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onCompleted() {
    }

}
