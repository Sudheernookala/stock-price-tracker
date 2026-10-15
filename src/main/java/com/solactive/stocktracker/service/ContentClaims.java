package com.solactive.stocktracker.service;

import com.solactive.stocktracker.exception.ContentInProgressException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Makes sure the same file content is never processed twice at the same time.
 *
 * <p>Before a file is processed, its content (identified by its SHA-256 checksum) is claimed. While
 * the claim is held, a second claim for the same checksum fails immediately; other checksums are
 * not affected, so different files are still processed in parallel. Uploads and the folder scan
 * share this one instance.</p>
 *
 * <p>The claims live in memory, so they protect a single application instance only.</p>
 */
@Component
public class ContentClaims {

    private final Set<String> claimed = ConcurrentHashMap.newKeySet();

    /**
     * Claims a checksum. Use with try-with-resources so the claim is always released.
     *
     * @param checksum SHA-256 of the file content
     * @return the claim; closing it releases the checksum
     * @throws ContentInProgressException when the same content is already being processed
     */
    public Claim claim(String checksum) {
        if (!claimed.add(checksum)) {
            throw new ContentInProgressException();
        }
        return new Claim(checksum);
    }

    /**
     * A held claim. Closing it more than once has no further effect.
     */
    public final class Claim implements AutoCloseable {

        private final String checksum;

        private Claim(String checksum) {
            this.checksum = checksum;
        }

        /** Releases the checksum so it can be claimed again. */
        @Override
        public void close() {
            claimed.remove(checksum);
        }
    }
}
