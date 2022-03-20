package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import pt.tecnico.bank.server.domain.exception.AccountAlreadyExistsException;
import pt.tecnico.bank.server.domain.exception.AccountDoesNotExistsException;
import pt.tecnico.bank.server.domain.exception.NotEnoughBalanceException;
import pt.tecnico.bank.server.domain.exception.SameAccountException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Facade class.
 * Contains the service operations.
 */
public class Server {

    private LinkedHashMap<ByteString, User> users = new LinkedHashMap<>();

    public Server() {}

    public synchronized boolean openAccount(ByteString pubKey, int balance) throws AccountAlreadyExistsException {
        if (users.containsKey(pubKey))
            throw new AccountAlreadyExistsException();

        User newUser = new User(pubKey, balance);
        users.put(pubKey, newUser);
        return true;
    }

    public synchronized boolean sendAmount(ByteString sourceKey, ByteString destinationKey, int amount)
            throws AccountDoesNotExistsException, SameAccountException, NotEnoughBalanceException {
        if (sourceKey == destinationKey) {
            throw new SameAccountException();
        } else if (!(users.containsKey(sourceKey) && users.containsKey(destinationKey))) {
            throw new AccountDoesNotExistsException();
        }

        User sourceUser = users.get(sourceKey);

        if (sourceUser.getBalance() < amount)
            throw new NotEnoughBalanceException();

        // TODO check if it was the source user that made the transfer ?????

        User destUser = users.get(destinationKey);
        HashMap<ByteString, Integer> destPendingTransfers = destUser.getPendingTransfers();
        destPendingTransfers.put(sourceKey, amount); // added to the dest pending transfers list
        destUser.setPendingTransfers(destPendingTransfers);
        users.put(destinationKey, destUser);

        return true;
    }

    public synchronized String[] checkAccount(ByteString pubKey) throws AccountDoesNotExistsException {
        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        String pendingTransfersAsString = null;
        HashMap<ByteString, Integer> pendingTransfers = users.get(pubKey).getPendingTransfers();

        if (pendingTransfers != null) {
            pendingTransfersAsString = mapToString(pendingTransfers);
        }

        return new String[]{ String.valueOf(users.get(pubKey).getBalance()), pendingTransfersAsString };
    }

    // TODO gui receive, tive de ir jantar

    public synchronized String audit(ByteString pubKey) throws AccountDoesNotExistsException {
        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        LinkedHashMap<ByteString, Integer> totalTransfers = users.get(pubKey).getTotalTransfers();

        if (totalTransfers != null)
            return mapToString(totalTransfers);

        return "No transfers found.";
    }

    private String mapToString(HashMap<ByteString, Integer> map) {
        return map.keySet().stream()
                .map(key -> key + "=" + map.get(key))
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
