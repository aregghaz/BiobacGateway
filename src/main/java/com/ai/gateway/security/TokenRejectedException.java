package com.ai.gateway.security;

/**
 * An access token that can't be used. The messages match the ones BioBacUser's JwtFilter sends, because the admin UI
 * refreshes the token only when it sees exactly "Access token expired".
 */
public class TokenRejectedException extends RuntimeException {

    public enum Reason {
        MISSING("Authentication required"),
        EXPIRED("Access token expired"),
        INVALID("Invalid access token.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final Reason reason;

    public TokenRejectedException(Reason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
