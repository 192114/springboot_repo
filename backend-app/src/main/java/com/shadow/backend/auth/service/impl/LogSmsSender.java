package com.shadow.backend.auth.service.impl;

import com.shadow.backend.auth.service.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** dev 环境模拟短信发送，仅记录发送事件，不输出手机号或验证码。 */
@Slf4j
@Profile("dev")
@Component
public class LogSmsSender implements SmsSender {

    @Override
    public void send(String phone, String code) {
        log.info("模拟短信验证码发送");
    }
}
