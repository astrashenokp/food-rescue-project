package com.example.foodrescue.common;

public final class SensitiveDataMasker {

    private SensitiveDataMasker() {
    }

    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(at);
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    public static String maskCode(String code) {
        if (code.length() <= 1) {
            return "*".repeat(code.length());
        }
        return "*".repeat(code.length() - 1) + code.charAt(code.length() - 1);
    }
}
