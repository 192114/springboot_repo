package com.shadow.backend.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** 登录失败次数限制，防止密码接口被暴力破解 */
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

    private static final String KEY_PREFIX = "login:fail:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);
    private static final DefaultRedisScript<Long> FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('TTL', KEYS[1]) < 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public boolean isLocked(String scene, String identifier) {
        String value = redisTemplate.opsForValue().get(buildKey(scene, identifier));
        return value != null && Integer.parseInt(value) >= MAX_ATTEMPTS;
    }

    public void onLoginFailed(String scene, String identifier) {
        redisTemplate.execute(FAILURE_SCRIPT, List.of(buildKey(scene, identifier)),
                Long.toString(LOCK_TTL.toSeconds()));
    }

    public void onLoginSucceeded(String scene, String identifier) {
        redisTemplate.delete(buildKey(scene, identifier));
    }

    private String buildKey(String scene, String identifier) {
        return KEY_PREFIX + scene + ":" + identifier;
    }
}
