package pt.tecnico.bank.server;

import java.util.ArrayList;
import java.util.List;

public class ResponseCollector {

    List<Object> responses = new ArrayList<>();

    public ResponseCollector() {
    }

    public void addResponse(Object res) {
        responses.add(res);
    }
}
