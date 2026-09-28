package com.shadow.backend.auth.service;

public interface SmsSender {

    void send(String phone, String code);
}
