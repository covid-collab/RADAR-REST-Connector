package org.radarbase.connect.rest.fitbit.user.firebase.exception;

import java.time.Instant;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.Response;

public class UnauthorizedException extends NotAuthorizedException {
    private String errorDescription="";
    private Long time;

    public UnauthorizedException(Object challenge, Object... moreChallenges) {
        super(challenge, moreChallenges);
        time = Instant.now().toEpochMilli();
    }

    public UnauthorizedException(String message, Object challenge, Object... moreChallenges) {
        super(message, challenge, moreChallenges);
        time = Instant.now().toEpochMilli();
    }

    public UnauthorizedException(Response response) {
        super(response);
        time = Instant.now().toEpochMilli();
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public Long getTime() {
        return time;
    }
}
