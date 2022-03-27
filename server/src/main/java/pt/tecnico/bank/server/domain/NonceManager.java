package pt.tecnico.bank.server.domain;

import pt.tecnico.bank.server.domain.exception.NonceAlreadyUsedException;
import pt.tecnico.bank.server.domain.exception.TimestampExpiredException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class NonceManager {

    /**
     * Contains all the nonces that were used inside the validity window sorted by timestamp.
     */
    static final List<NonceEntry> nonces = new ArrayList<>() {
        public boolean add(NonceEntry nonceEntry) {
            super.add(nonceEntry);
            nonces.sort(Comparator.comparing(NonceEntry::getTimestamp));
            return true;
        }
    };

    private volatile long lastCleaned = 0;

    // we'll default to a 10-minute validity window, otherwise the amount of memory used on nonces can get quite large.
    private long validityWindowSeconds = 60 * 10;

    public void validateNonce(String nonce, long timestamp) throws TimestampExpiredException, NonceAlreadyUsedException {
        if (System.currentTimeMillis() / 1000 - timestamp > getValidityWindowSeconds()) {
            throw new TimestampExpiredException();
        }

        NonceEntry entry = new NonceEntry(timestamp, nonce);

        synchronized (nonces) {
            if (nonces.contains(entry))
                throw new NonceAlreadyUsedException();

            nonces.add(entry);
            cleanupNonces();
        }
    }

    private void cleanupNonces() {
        long now = System.currentTimeMillis() / 1000;

        if (now - lastCleaned > 1) {    // One second is small enough that cleaning up does not become too expensive.
            Iterator<NonceEntry> iterator = nonces.iterator();
            while (iterator.hasNext()) {
                NonceEntry nextNonce = iterator.next();
                long difference = now - nextNonce.timestamp;
                if (difference > getValidityWindowSeconds()) {
                    iterator.remove();
                } else {
                    break; // we can break because the list is sorted
                }
            }
            // keep track of when cleanupNonces last ran
            lastCleaned = now;
        }
    }

    public long getValidityWindowSeconds() {
        return validityWindowSeconds;
    }

    public void setValidityWindowSeconds(long validityWindowSeconds) {
        this.validityWindowSeconds = validityWindowSeconds;
    }

    /**
     * Representation of a nonce
     */
    static class NonceEntry {

        private final long timestamp;
        private final String nonce;

        public NonceEntry(long timestamp, String nonce) {
            this.timestamp = timestamp;
            this.nonce = nonce;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getNonce() {
            return nonce;
        }
    }
}