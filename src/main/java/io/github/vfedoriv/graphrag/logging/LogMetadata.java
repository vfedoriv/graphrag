package io.github.vfedoriv.graphrag.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Safe metadata helpers for application logs and controlled AI observations.
 */
public final class LogMetadata {

    private static final int DEFAULT_CONTENT_PREVIEW_LENGTH = 300;
    private static final int FINGERPRINT_LENGTH = 16;

    private LogMetadata() {
    }

    public static int length(String value) {
        return value == null ? 0 : value.length();
    }

    /**
     * Returns a content preview for the centralized, privacy-controlled observation path only.
     */
    public static String contentPreview(String value) {
        return contentPreview(value, DEFAULT_CONTENT_PREVIEW_LENGTH);
    }

    public static String contentPreview(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replaceAll("\\s+", " ").trim();
        if (singleLine.length() <= maxLength) {
            return singleLine;
        }
        return singleLine.substring(0, Math.max(0, maxLength)) + "...";
    }

    public static String fingerprint(String value) {
        if (value == null) {
            return "none";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return "sha256:" + hex.substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            return "unavailable";
        }
    }

    public static String exceptionType(Throwable throwable) {
        return throwable == null ? "none" : throwable.getClass().getSimpleName();
    }

    public static String exceptionMessageFingerprint(Throwable throwable) {
        return throwable == null ? "none" : fingerprint(throwable.getMessage());
    }

    public static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
