package com.hotelai.reservation.integration;

public class IntegrationException extends RuntimeException {
    private final int upstreamStatus;

    public IntegrationException(String message) {
        this(message, 503, null);
    }

    public IntegrationException(String message, Throwable cause) {
        this(message, 503, cause);
    }

    private IntegrationException(String message, int upstreamStatus, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
    }

    static IntegrationException from(String service, int status) {
        String detail = status == 409 || status == 422
                ? service + " service rejected the request"
                : service + " service failed with status " + status;
        return new IntegrationException(detail, status, null);
    }

    public int upstreamStatus() { return upstreamStatus; }
}
