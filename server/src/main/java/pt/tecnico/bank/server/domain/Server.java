package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import pt.tecnico.bank.server.domain.exception.AccountAlreadyExistsException;
import pt.tecnico.bank.server.domain.exception.AccountDoesNotExistsException;
import pt.tecnico.bank.server.domain.exception.NotEnoughBalanceException;
import pt.tecnico.bank.server.domain.exception.SameAccountException;

import java.util.HashMap;
import java.util.LinkedHashMap;
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

        System.out.println(users.get(destinationKey).toString());

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

    public synchronized boolean receiveAmount(ByteString pubKey) throws AccountDoesNotExistsException {
        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        User user = users.get(pubKey);
        HashMap<ByteString, Integer> pendingTransfers = user.getPendingTransfers();

        pendingTransfers.forEach((key, value) -> {
            transferAmount(key, pubKey, -value); // take from senders
            transferAmount(pubKey, key, value); // transfer to receiver
        });

        pendingTransfers.clear();
        user.setPendingTransfers(pendingTransfers); // clear the list
        users.put(pubKey, user); // update user

        return true;
    }

    public synchronized String audit(ByteString pubKey) throws AccountDoesNotExistsException {
        if (!(users.containsKey(pubKey)))
            throw new AccountDoesNotExistsException();

        LinkedHashMap<ByteString, Integer> totalTransfers = users.get(pubKey).getTotalTransfers();

        if (totalTransfers != null)
            return mapToString(totalTransfers);

        return "No transfers found.";
    }

    // ------------------------------------ AUX -------------------------------------

    private void transferAmount(ByteString senderKey, ByteString receiverKey, int amount) {
        User user = users.get(senderKey);
        LinkedHashMap<ByteString, Integer> totalTransfers = user.getTotalTransfers();
        totalTransfers.put(receiverKey, amount);
        user.setTotalTransfers(totalTransfers);
        user.setBalance(user.getBalance() + amount);
        users.put(senderKey, user);
    }

    private String mapToString(HashMap<ByteString, Integer> map) {
        return map.keySet().stream()
                .map(key -> key.toStringUtf8() + ": " + map.get(key))
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
