package com.solactive.stocktracker.service;

import com.solactive.stocktracker.exception.InvalidFileException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Component;

/**
 * Computes the SHA-256 checksum of a file by streaming it (constant memory). Used to recognise a
 * file whose content was already processed.
 */
@Component
public class ChecksumCalculator {

    private static final int BUFFER_SIZE = 64 * 1024;

    /**
     * Computes the checksum.
     *
     * @param fileName name used in error messages
     * @param source   content source
     * @return lower-case hex SHA-256
     * @throws InvalidFileException when the content cannot be read
     */
    public String sha256(String fileName, InputStreamSource source) {
        MessageDigest digest = newDigest();
        try (InputStream in = source.getInputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException ex) {
            throw InvalidFileException.unreadable(fileName, ex.getMessage());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is available on every Java platform", ex);
        }
    }
}
