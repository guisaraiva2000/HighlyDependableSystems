package pt.tecnico.bank.server.domain;

import com.google.protobuf.ByteString;
import pt.tecnico.bank.server.domain.exception.AccountAlreadyExistsException;
import pt.tecnico.bank.server.domain.exception.AccountDoesNotExistsException;
import pt.tecnico.bank.server.domain.exception.NotEnoughBalanceException;
import pt.tecnico.bank.server.domain.exception.SameAccountException;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * Facade class.
 * Contains the service operations.
 */
public class Server {

    private final LinkedHashMap<byte[], User> users = new LinkedHashMap<>();

    public Server() {}

    public synchronized boolean openAccount(ByteString pubKey, int balance) throws AccountAlreadyExistsException {
        byte[] pubKeyBites = keyToBytes(pubKey);

        System.out.println("Public: " + pubKeyBites);

        if (users.containsKey(pubKeyBites))
            throw new AccountAlreadyExistsException();

        User newUser = new User(pubKeyBites, balance);
        users.put(pubKeyBites, newUser);
        return true;
    }

    public synchronized boolean sendAmount(ByteString sourceKey, ByteString destinationKey, int amount)
            throws AccountDoesNotExistsException, SameAccountException, NotEnoughBalanceException {
        byte[] sourceKeyBites = keyToBytes(sourceKey);
        byte[] destinationKeyBites = keyToBytes(destinationKey);

        if (sourceKey == destinationKey) {
            throw new SameAccountException();
        } else if (!(users.containsKey(sourceKeyBites) && users.containsKey(destinationKeyBites))) {
            throw new AccountDoesNotExistsException();
        }

        User sourceUser = users.get(sourceKeyBites);

        if (sourceUser.getBalance() < amount)
            throw new NotEnoughBalanceException();

        // TODO check if it was the source user that made the transfer ?????

        User destUser = users.get(destinationKeyBites);
        LinkedList<Transfer> destPendingTransfers = destUser.getPendingTransfers();
        destPendingTransfers.add(new Transfer(sourceKeyBites, amount)); // added to the dest pending transfers list
        destUser.setPendingTransfers(destPendingTransfers);
        users.put(destinationKeyBites, destUser);

        System.out.println(users.get(destinationKeyBites).toString());

        return true;
    }

    public synchronized String[] checkAccount(ByteString pubKey) throws AccountDoesNotExistsException {
        byte[] pubKeyBites = keyToBytes(pubKey);

        if (!(users.containsKey(pubKeyBites)))
            throw new AccountDoesNotExistsException();

        String pendingTransfersAsString = null;
        LinkedList<Transfer> pendingTransfers = users.get(pubKeyBites).getPendingTransfers();

        if (pendingTransfers != null) {
            pendingTransfersAsString = getTransfersAsString(pendingTransfers);
        }

        return new String[]{ String.valueOf(users.get(pubKeyBites).getBalance()), pendingTransfersAsString };
    }

    public synchronized boolean receiveAmount(ByteString pubKey) throws AccountDoesNotExistsException {
        byte[] pubKeyBites = keyToBytes(pubKey);

        if (!(users.containsKey(pubKeyBites)))
            throw new AccountDoesNotExistsException();

        User user = users.get(pubKeyBites);
        LinkedList<Transfer> pendingTransfers = user.getPendingTransfers();

        pendingTransfers.forEach(transfer -> {
            transferAmount(transfer.getDestination(), pubKeyBites, -transfer.getAmount()); // take from senders
            transferAmount(pubKeyBites, transfer.getDestination(), transfer.getAmount()); // transfer to receiver
        });

        pendingTransfers.clear();
        user.setPendingTransfers(pendingTransfers); // clear the list
        users.put(pubKeyBites, user); // update user

        return true;
    }

    public synchronized String audit(ByteString pubKey) throws AccountDoesNotExistsException {
        byte[] pubKeyBites = keyToBytes(pubKey);

        if (!(users.containsKey(pubKeyBites)))
            throw new AccountDoesNotExistsException();

        LinkedList<Transfer> totalTransfers = users.get(pubKeyBites).getTotalTransfers();

        if (totalTransfers != null)
            return getTransfersAsString(totalTransfers);

        return "No transfers found.";
    }

    // ------------------------------------ AUX -------------------------------------

    private void transferAmount(byte[] senderKey, byte[] receiverKey, int amount) {
        User user = users.get(senderKey);
        LinkedList<Transfer> totalTransfers = user.getTotalTransfers();
        totalTransfers.add(new Transfer(receiverKey, amount));
        user.setTotalTransfers(totalTransfers);
        user.setBalance(user.getBalance() + amount);
        users.put(senderKey, user);
    }

    private String getTransfersAsString(LinkedList<Transfer> pendingTransfers) {
        return pendingTransfers.stream().map(Transfer::toString).collect(Collectors.joining());
    }

    private byte[] keyToBytes(ByteString pubKey) {
        byte[] pubKeyBites = new byte[294];
        pubKey.copyTo(pubKeyBites, 0);
        return pubKeyBites;
    }
}
