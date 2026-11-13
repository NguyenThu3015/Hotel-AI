package com.hotelai.customer.api;

public final class PiiMasking {
    private PiiMasking() {}

    public static String email(String value) {
        if (value == null || value.isBlank()) return value;
        int at = value.indexOf('@');
        if (at <= 0) return "***";
        String local = value.substring(0, at);
        String visible = local.substring(0, 1);
        return visible + "***" + value.substring(at);
    }

    public static String phone(String value) {
        if (value == null || value.isBlank()) return value;
        String digits = value.replaceAll("\\D", "");
        if (digits.length() <= 4) return "****";
        return "***" + digits.substring(digits.length() - 4);
    }
}
