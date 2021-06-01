package org.radarbase.connect.rest.fitbit.user.firebase;

import com.google.cloud.firestore.annotation.Exclude;
import com.google.cloud.firestore.annotation.PropertyName;
import java.time.Instant;

public class AuthResult {
    private int httpCode;
    private String message;
    private Long time;
    private String errorDescription;

    public AuthResult() {
        httpCode = 200;
        message = "";
        time = Instant.now().toEpochMilli();
        errorDescription = "";
    }

    public AuthResult(int httpCode, String message, Long time) {
        this.httpCode = httpCode;
        this.message = message;
        this.time = time;
        errorDescription = "";
    }

    public AuthResult(int httpCode, String message, Long time, String errorDescription) {
        this.httpCode = httpCode;
        this.message = message;
        this.time = time;
        this.errorDescription = errorDescription;
    }

    public AuthResult(int httpCode, String message) {
        this.httpCode = httpCode;
        this.message = message;
        time = Instant.now().toEpochMilli();
        errorDescription = "";
    }

    @PropertyName("http_code")
    public int getHttpCode() {
        return httpCode;
    }

    @PropertyName("http_code")
    public void setHttpCode(int httpCode) {
        this.httpCode = httpCode;
    }

    @PropertyName("error_message")
    public String getMessage() {
        return message;
    }

    @PropertyName("error_message")
    public void setMessage(String message) {
        this.message = message;
    }

    @PropertyName("time")
    public Long getTime() {
        return time;
    }

    @PropertyName("time")
    public void setTime(Long time) {
        this.time = time;
    }

    @PropertyName("error_description")
    public String getErrorDescription() {
        return errorDescription;
    }

    @PropertyName("error_description")
    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    @Exclude
    @Override
    public String toString() {
        return "AuthResult{" +
                "httpCode=" + httpCode +
                ", message='" + message + '\'' +
                ", time=" + time +
                ", errorDescription='" + errorDescription + '\'' +
                '}';
    }
}
