package com.shadow.backend.common.util;

public final class PhoneMaskUtil {

    private PhoneMaskUtil() {
    }

    public static String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        if (phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
