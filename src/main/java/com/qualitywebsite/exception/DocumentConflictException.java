package com.qualitywebsite.exception;

/**
 * Thrown when a JPA Optimistic Locking conflict is detected during a DMS write
 * operation (update metadata, approve, reject, archive, upload new version).
 *
 * This is a business-layer translation of Spring's
 * {@code ObjectOptimisticLockingFailureException}.  It is caught by
 * {@link GlobalExceptionHandler} and returned as HTTP 409 Conflict — no stack
 * trace is exposed to the client.
 */
public class DocumentConflictException extends RuntimeException {

    private final Long documentMasterId;
    private final Long latestEntityVersion;

    public DocumentConflictException(String message, Long documentMasterId, Long latestEntityVersion) {
        super(message);
        this.documentMasterId    = documentMasterId;
        this.latestEntityVersion = latestEntityVersion;
    }

    /** Convenience constructor when the latest version is not available. */
    public DocumentConflictException(String message, Long documentMasterId) {
        this(message, documentMasterId, null);
    }

    public Long getDocumentMasterId() {
        return documentMasterId;
    }

    public Long getLatestEntityVersion() {
        return latestEntityVersion;
    }
}
