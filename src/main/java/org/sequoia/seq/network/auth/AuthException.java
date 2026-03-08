package org.sequoia.seq.network.auth;

public class AuthException extends RuntimeException {

    private final AuthErrorCode code;
    private final boolean retryable;

    public AuthException(AuthErrorCode code, String message) {
        this(code, message, false, null);
    }

    public AuthException(AuthErrorCode code, String message, boolean retryable) {
        this(code, message, retryable, null);
    }

    public AuthException(AuthErrorCode code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public AuthErrorCode getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getStableCode() {
        return code.code();
    }
}
