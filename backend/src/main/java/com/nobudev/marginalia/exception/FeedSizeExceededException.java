package com.nobudev.marginalia.exception;

/**
 * Thrown when an incoming feed payload exceeds the configured maximum allowable size.
 */
public class FeedSizeExceededException extends RuntimeException {

    private final String url;
    private final long maxBytes;
    private final Long actualBytes;

    public FeedSizeExceededException(String message) {
        super(message);
        this.url = null;
        this.maxBytes = 0;
        this.actualBytes = null;
    }

    public FeedSizeExceededException(String url, long maxBytes, Long actualBytes) {
        super(formatMessage(url, maxBytes, actualBytes));
        this.url = url;
        this.maxBytes = maxBytes;
        this.actualBytes = actualBytes;
    }

    private static String formatMessage(String url, long maxBytes, Long actualBytes) {
        if (actualBytes != null && actualBytes > 0) {
            return String.format("Feed response from %s exceeds maximum allowed size of %d bytes (actual: %d bytes)",
                    url, maxBytes, actualBytes);
        }
        return String.format("Feed response from %s exceeds maximum allowed size of %d bytes",
                url, maxBytes);
    }

    public String getUrl() {
        return url;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public Long getActualBytes() {
        return actualBytes;
    }
}
