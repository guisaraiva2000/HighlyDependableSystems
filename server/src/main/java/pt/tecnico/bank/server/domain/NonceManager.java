/*package pt.tecnico.bank.server.domain;

import java.security.PublicKey;
import java.util.Iterator;
import java.util.TreeSet;

public class NonceManager {

    /**
     * Contains all the nonces that were used inside the validity window.
     //
    static final TreeSet<NonceEntry> NONCES = new TreeSet<>();

    private volatile long lastCleaned = 0;

    // we'll default to a 10 minute validity window, otherwise the amount of memory used on NONCES can get quite large.
    private long validityWindowSeconds = 60 * 10;

    public void validateNonce(PublicKey publicKey, long timestamp, String nonce) {
        if (System.currentTimeMillis() / 1000 - timestamp > getValidityWindowSeconds()) {
            throw new CredentialsExpiredException("Expired timestamp.");
        }

        NonceEntry entry = new NonceEntry(publicKey, timestamp, nonce);

        synchronized (NONCES) {
            if (NONCES.contains(entry)) {
                throw new NonceAlreadyUsedException("Nonce already used");
            } else {
                NONCES.add(entry);
            }
            cleanupNonces();
        }
    }

    private void cleanupNonces() {
        long now = System.currentTimeMillis() / 1000;
        // don't clean out the NONCES for each request, this would cause the service to be constantly locked on this
        // loop under load. One second is small enough that cleaning up does not become too expensive.
        // Also see SECOAUTH-180 for reasons this class was refactored.
        if (now - lastCleaned > 1) {
            Iterator<NonceEntry> iterator = NONCES.iterator();
            while (iterator.hasNext()) {
                // the nonces are already sorted, so simply iterate and remove until the first nonce within the validity
                // window.
                NonceEntry nextNonce = iterator.next();
                long difference = now - nextNonce.timestamp;
                if (difference > getValidityWindowSeconds()) {
                    iterator.remove();
                } else {
                    break;
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
     * Representation of a nonce with the right hashCode, equals, and compareTo methods for the TreeSet approach above
     * to work.
     //
    static class NonceEntry implements Comparable<NonceEntry> {
        private final PublicKey consumerKey;

        private final long timestamp;

        private final String nonce;

        public NonceEntry(PublicKey consumerKey, long timestamp, String nonce) {
            this.consumerKey = consumerKey;
            this.timestamp = timestamp;
            this.nonce = nonce;
        }

        @Override
        public int hashCode() {
            return consumerKey.hashCode() * nonce.hashCode() * Long.valueOf(timestamp).hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NonceEntry)) {
                return false;
            }
            NonceEntry arg = (NonceEntry) obj;
            return timestamp == arg.timestamp && consumerKey.equals(arg.consumerKey) && nonce.equals(arg.nonce);
        }

        public int compareTo(NonceEntry o) {
            // sort by timestamp
            if (timestamp < o.timestamp) {
                return -1;
            }
            else if (timestamp == o.timestamp) {
                int consumerKeyCompare = consumerKey.compareTo(o.consumerKey);
                if (consumerKeyCompare == 0) {
                    return nonce.compareTo(o.nonce);
                }
                else {
                    return consumerKeyCompare;
                }
            }
            else {
                return 1;
            }
        }

        @Override
        public String toString() {
            return timestamp + " " + consumerKey + " " + nonce;
        }
    }
}*/