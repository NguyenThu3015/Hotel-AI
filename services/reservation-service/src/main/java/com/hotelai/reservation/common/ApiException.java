package com.hotelai.reservation.common;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    private ApiException(int status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public static ApiException validation(String detail) {
        return new ApiException(400, "validation", detail);
    }

    public static ApiException notFound(String detail) {
        return new ApiException(404, "not-found", detail);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(409, "conflict", detail);
    }

    public static ApiException versionConflict() {
        return new ApiException(409, "optimistic-lock", "Reservation version is stale; reload before retrying");
    }

    public static ApiException upstream(String detail) {
        return new ApiException(502, "invalid-upstream-response", detail);
    }

    public int status() { return status; }
    public String code() { return code; }
}
