package com.shadow.backend.auth.service;

import com.shadow.backend.auth.constant.SmsScene;

public interface SmsService {

    void sendCode(String phone, SmsScene scene);

    void verifyCode(String phone, SmsScene scene, String code);
}
