package com.shadow.backend.auth.constant;

public enum SmsScene {

    LOGIN,
    REGISTER,
    RESET_PASSWORD;

    public static SmsScene fromName(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
