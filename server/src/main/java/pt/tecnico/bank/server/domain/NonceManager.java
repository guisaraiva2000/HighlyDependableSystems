package pt.tecnico.bank.server.domain;

import java.io.Serializable;
import java.util.*;

public class NonceManager implements Serializable {

    /**
     * Contains all the nonces that were used inside the validity window sorted by timestamp.
     */
    static final List<NonceEntry> nonces = new ArrayList<>();

    private volatile long lastCleaned = 0;

    // we'll default to a 10-minute validity window, otherwise the amount of memory used on nonces can get quite large.
    private long validityWindowSeconds = 60 * 10;

    public boolean validateNonce(long nonce, long timestamp)  {
        if (System.currentTimeMillis() / 1000 - timestamp > getValidityWindowSeconds())
            return false;

        NonceEntry entry = new NonceEntry(timestamp, nonce);

        if (nonces.contains(entry))
            return false;

        nonces.add(entry);
        cleanupNonces();

        return true;
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
                    break; // we can break because the list is sorted by timestamp
                }
            }
            // keep track of last cleanup
            lastCleaned = now;
        }
    }

    public long getValidityWindowSeconds() {
        return validityWindowSeconds;
    }

    public void setValidityWindowSeconds(long validityWindowSeconds) {
        this.validityWindowSeconds = validityWindowSeconds;
    }

    public String getNonces() {
        StringBuilder builder = new StringBuilder();
        for (NonceEntry ne : nonces) {
            builder.append("(").append(ne.nonce).append(", ").append(ne.timestamp).append(")");
        }
        return builder.toString();
    }

    /**
     * Representation of a nonce -> (rand, timestamp)
     */
    static class NonceEntry implements Serializable{

        private final long timestamp;
        private final long nonce;

        public NonceEntry(long timestamp, long nonce) {
            this.timestamp = timestamp;
            this.nonce = nonce;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getNonce() {
            return nonce;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NonceEntry that = (NonceEntry) o;
            return timestamp == that.timestamp && Objects.equals(nonce, that.nonce);
        }

        @Override
        public int hashCode() {
            return Objects.hash(timestamp, nonce);
        }
    }
}