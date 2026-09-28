package com.shadow.backend.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shadow.backend.auth.constant.SmsScene;
import com.shadow.backend.auth.entity.SmsLog;
import com.shadow.backend.auth.mapper.SmsLogMapper;
import com.shadow.backend.account.response.AuthResultCode;
import com.shadow.backend.auth.service.SmsSender;
import com.shadow.backend.auth.service.SmsService;
import com.shadow.backend.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
public class SmsServiceImpl implements SmsService {

    private static final String CODE_KEY_PREFIX = "sms:code:";
    private static final String LIMIT_KEY_PREFIX = "sms:limit:";
    private static final String DAILY_KEY_PREFIX = "sms:daily:";
    private static final String ATTEMPT_KEY_PREFIX = "sms:attempt:";
    /** 数据库中验证码字段仅存固定掩码，不落明文 */
    private static final String CODE_MASK = "******";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration LIMIT_TTL = Duration.ofSeconds(60);
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final DefaultRedisScript<Long> DAILY_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIREAT', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private static final DefaultRedisScript<Long> STORE_CODE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);

    /**
     * 验证码核销原子脚本：比对成功则删除验证码与错误计数；失败则累加错误计数，
     * 达到上限即作废验证码。返回值：1 成功 / 0 不匹配 / -1 不存在 / -2 错误次数过多。
     */
    private static final DefaultRedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('GET', KEYS[1])
            if stored == false then
                return -1
            end
            if stored == ARGV[1] then
                redis.call('DEL', KEYS[1], KEYS[2])
                return 1
            end
            local attempts = redis.call('INCR', KEYS[2])
            redis.call('PEXPIRE', KEYS[2], redis.call('PTTL', KEYS[1]))
            if attempts >= tonumber(ARGV[2]) then
                redis.call('DEL', KEYS[1], KEYS[2])
                return -2
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SmsLogMapper smsLogMapper;
    private final SmsSender smsSender;
    private final int dailyLimit;

    public SmsServiceImpl(StringRedisTemplate redisTemplate, SmsLogMapper smsLogMapper,
                          SmsSender smsSender,
                          @Value("${app.sms.daily-limit:10}") int dailyLimit) {
        this.redisTemplate = redisTemplate;
        this.smsLogMapper = smsLogMapper;
        this.smsSender = smsSender;
        this.dailyLimit = dailyLimit;
    }

    @Override
    public void sendCode(String phone, SmsScene scene) {
        String limitKey = LIMIT_KEY_PREFIX + phone;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(limitKey, "1", LIMIT_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            throw new BusinessException(AuthResultCode.SMS_CODE_SEND_TOO_FREQUENT);
        }

        String dailyKey = DAILY_KEY_PREFIX + phone;
        ZonedDateTime now = ZonedDateTime.now();
        long dailyExpiresAt = now.toLocalDate().plusDays(1).atStartOfDay(now.getZone())
                .toInstant().toEpochMilli();
        Long sentToday = redisTemplate.execute(DAILY_SCRIPT, List.of(dailyKey),
                String.valueOf(dailyExpiresAt));
        if (sentToday != null && sentToday > dailyLimit) {
            throw new BusinessException(AuthResultCode.SMS_CODE_DAILY_LIMIT);
        }

        String code = generateCode();
        String codeKey = CODE_KEY_PREFIX + scene.name() + ":" + phone;
        String attemptKey = ATTEMPT_KEY_PREFIX + scene.name() + ":" + phone;
        redisTemplate.execute(STORE_CODE_SCRIPT, List.of(codeKey, attemptKey),
                code, String.valueOf(CODE_TTL.toSeconds()));

        SmsLog smsLog = new SmsLog();
        smsLog.setPhone(phone);
        smsLog.setScene(scene.name());
        smsLog.setCode(CODE_MASK);
        smsLog.setStatus(0);
        smsLog.setSendTime(LocalDateTime.now());
        smsLogMapper.insert(smsLog);

        smsSender.send(phone, code);
        log.info("验证码已发送: scene={}", scene);
    }

    @Override
    public void verifyCode(String phone, SmsScene scene, String code) {
        String codeKey = CODE_KEY_PREFIX + scene.name() + ":" + phone;
        String attemptKey = ATTEMPT_KEY_PREFIX + scene.name() + ":" + phone;
        Long result = redisTemplate.execute(VERIFY_SCRIPT, List.of(codeKey, attemptKey),
                code, String.valueOf(MAX_VERIFY_ATTEMPTS));

        if (result == null || result == -1) {
            throw new BusinessException(AuthResultCode.SMS_CODE_NOT_FOUND);
        }
        if (result == -2) {
            throw new BusinessException(AuthResultCode.SMS_CODE_EXCEED_ATTEMPTS);
        }
        if (result == 0) {
            throw new BusinessException(AuthResultCode.SMS_CODE_INVALID);
        }

        SmsLog latestLog = smsLogMapper.selectOne(new LambdaQueryWrapper<SmsLog>()
                .eq(SmsLog::getPhone, phone)
                .eq(SmsLog::getScene, scene.name())
                .eq(SmsLog::getStatus, 0)
                .orderByDesc(SmsLog::getId)
                .last("LIMIT 1"));
        if (latestLog != null) {
            latestLog.setStatus(1);
            latestLog.setVerifiedTime(LocalDateTime.now());
            smsLogMapper.updateById(latestLog);
        }
    }

    private String generateCode() {
        return String.valueOf(SECURE_RANDOM.nextInt(100000, 1000000));
    }
}
