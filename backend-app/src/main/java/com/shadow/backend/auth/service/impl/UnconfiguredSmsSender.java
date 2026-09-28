package com.shadow.backend.auth.service.impl;

import com.shadow.backend.account.response.AuthResultCode;
import com.shadow.backend.auth.service.SmsSender;
import com.shadow.backend.common.exception.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * prod 环境占位实现：未接入真实短信供应商时，发送直接返回业务错误，不静默丢弃。
 * 接入真实供应商后以新的 @Profile("prod") 实现替换本类。
 */
@Profile("prod")
@Component
public class UnconfiguredSmsSender implements SmsSender {

    @Override
    public void send(String phone, String code) {
        throw new BusinessException(AuthResultCode.SMS_NOT_CONFIGURED);
    }
}
