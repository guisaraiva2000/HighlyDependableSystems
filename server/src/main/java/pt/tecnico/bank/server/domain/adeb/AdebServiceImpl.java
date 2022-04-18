package pt.tecnico.bank.server.domain.adeb;

import io.grpc.stub.StreamObserver;
import pt.tecnico.bank.server.domain.ServerBackend;
import pt.tecnico.bank.server.grpc.Adeb.EchoRequest;
import pt.tecnico.bank.server.grpc.Adeb.EchoResponse;
import pt.tecnico.bank.server.grpc.Adeb.ReadyRequest;
import pt.tecnico.bank.server.grpc.Adeb.ReadyResponse;
import pt.tecnico.bank.server.grpc.AdebServiceGrpc;

public class AdebServiceImpl extends AdebServiceGrpc.AdebServiceImplBase {

    private final ServerBackend serverBackend;

    public AdebServiceImpl(ServerBackend serverBackend) {
        this.serverBackend = serverBackend;
    }

    @Override
    public synchronized void echo(EchoRequest req, StreamObserver<EchoResponse> responseObserver) {

        serverBackend.echo(req.getKey(), req.getSname(), req.getInput(), req.getNonce(), req.getTimestamp(), req.getSignature());

    }

    @Override
    public synchronized void ready(ReadyRequest req, StreamObserver<ReadyResponse> responseObserver) {

        serverBackend.ready(req.getKey(), req.getSname(), req.getInput(), req.getNonce(), req.getTimestamp(), req.getSignature());

    }

}
