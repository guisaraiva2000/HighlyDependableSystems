package pt.tecnico.bank.server.domain.adeb;

import java.util.HashMap;

public class AdebManager {

    private final HashMap<String, AdebInstance> adebInstances = new HashMap<>();
    private final int nByzantineServers;

    public AdebManager(int nByzantineServers) {
        this.nByzantineServers = nByzantineServers;
    }

    public void addInstance(String input, AdebInstance adebInstance) {
        adebInstances.put(input, adebInstance);
    }

    public AdebInstance getOrAddAdebInstance(String input) {
        if (adebInstances.containsKey(input))
            return adebInstances.get(input);

        AdebInstance adebInstance = new AdebInstance(this.nByzantineServers);
        addInstance(input, adebInstance);

        return adebInstance;
    }

    public void removeAdebInstance(String input) {
        this.adebInstances.remove(input);
    }
}
