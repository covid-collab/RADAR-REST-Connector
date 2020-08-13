package org.radarbase.connect.rest.fitbit.user.firebase.exception;

import org.radarbase.connect.rest.fitbit.user.firebase.AuthResult;

public class HttpException extends RuntimeException {
    private final AuthResult authResult;

    public HttpException(AuthResult authResult) {
        super(authResult.getMessage());
        this.authResult = authResult;
    }

    public HttpException(String message, AuthResult authResult) {
        super(message);
        this.authResult = authResult;
    }

    public HttpException(String message, Throwable cause, AuthResult authResult) {
        super(message, cause);
        this.authResult = authResult;
    }

    public HttpException(Throwable cause, AuthResult authResult) {
        super(cause);
        this.authResult = authResult;
    }

    public HttpException(String message, Throwable cause, boolean enableSuppression,
                         boolean writableStackTrace, AuthResult authResult) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.authResult = authResult;
    }

    public AuthResult getAuthResult() {
        return authResult;
    }

    @Override
    public String toString() {
        return "HttpException{" +
                "authResult=" + authResult +
                '}';
    }
}
