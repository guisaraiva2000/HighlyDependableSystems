package pt.tecnico.bank.server.domain.adeb;

import pt.tecnico.bank.server.grpc.Server.AdebProof;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class AdebInstance {

    private final AdebFrontend adebFrontend;
    private final int byzantineEchoQuorum;
    private final int byzantineReadyQuorum;
    private byte[] input = null;
    private boolean sentEcho = false;
    private boolean sentReady = false;
    private boolean delivered = false;
    private final List<byte[]> echos = new ArrayList<>();
    private final List<byte[]> readys = new ArrayList<>();
    private final List<AdebProof> adebProof = new ArrayList<>();
    private CountDownLatch latch;

    public AdebInstance(int nByzantineServers) {
        this.adebFrontend = new AdebFrontend(nByzantineServers);

        int nServers = 3 * nByzantineServers + 1;
        this.byzantineEchoQuorum = (nServers + nByzantineServers) / 2 + 1;      //  > (N + f) / 2
        this.byzantineReadyQuorum = 2 * nByzantineServers + 1;                  //  > 2f
    }

    public AdebFrontend getAdebFrontend() {
        return adebFrontend;
    }

    public int getByzantineEchoQuorum() {
        return byzantineEchoQuorum;
    }

    public int getByzantineReadyQuorum() {
        return byzantineReadyQuorum;
    }

    public byte[] getInput() {
        return input;
    }

    public void setInput(byte[] input) {
        this.input = input;
    }

    public boolean isSentEcho() {
        return sentEcho;
    }

    public void setSentEcho(boolean sentEcho) {
        this.sentEcho = sentEcho;
    }

    public boolean isSentReady() {
        return sentReady;
    }

    public void setSentReady(boolean sentReady) {
        this.sentReady = sentReady;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public List<byte[]> getEchos() {
        return echos;
    }

    public void addEcho(byte[] echo) {
        this.echos.add(echo);
    }

    public List<byte[]> getReadys() {
        return readys;
    }

    public void addReady(byte[] ready) {
        this.readys.add(ready);
    }

    public List<AdebProof> getAdebProof() {
        return adebProof;
    }

    public void addAdebProof(AdebProof adebProof) {
        this.adebProof.add(adebProof);
    }

    public CountDownLatch getLatch() {
        return latch;
    }

    public void setLatch(CountDownLatch latch) {
        this.latch = latch;
    }

    public void countDown() {
        this.latch.countDown();
    }
}
