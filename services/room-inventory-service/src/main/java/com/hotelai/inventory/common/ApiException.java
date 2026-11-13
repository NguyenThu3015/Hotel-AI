package com.hotelai.inventory.common;

public class ApiException extends RuntimeException {
    private final int status;
    private final String problemCode;

    public ApiException(int status, String problemCode, String message) {
        super(message);
        this.status = status;
        this.problemCode = problemCode;
    }

    public int status() {
        return status;
    }

    public String problemCode() {
        return problemCode;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(400, code, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "not-found", message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(409, code, message);
    }
}
