package pt.tecnico.bank.client.frontend;

import java.util.concurrent.ConcurrentHashMap;

public class ResponseCollector {

    ConcurrentHashMap<String, Object> responses = new ConcurrentHashMap<>();

    public ResponseCollector() {
    }

    public void addResponse(String sName, Object res) {
        responses.put(sName, res);
    }
}
