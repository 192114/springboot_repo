package com.shadow.backend.account.session.service.impl;

import com.shadow.backend.account.response.AuthResultCode;
import com.shadow.backend.account.session.service.TokenService;
import com.shadow.backend.account.session.vo.TokenPair;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.account.session.util.StpAppUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    /** 用户级撤销注册表：userId -> 该用户当前有效的 Refresh Token 集合 */
    private static final String USER_REFRESH_SET_PREFIX = "auth:refresh:user:";
    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final String SESSION_REFRESH_TOKEN_KEY = "refreshToken";

    private static final DefaultRedisScript<Long> REGISTER_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
            redis.call('SADD', KEYS[2], ARGV[2])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            return 1
            """, Long.class);

    // GETDEL 仍要求 Redis 6.2+，核对与轮换必须在同一脚本中完成。
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            redis.call('GETDEL', KEYS[1])
            redis.call('SREM', KEYS[3], ARGV[2])
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[4])
            redis.call('SADD', KEYS[3], ARGV[3])
            redis.call('EXPIRE', KEYS[3], ARGV[4])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
            local tokens = redis.call('SMEMBERS', KEYS[1])
            for _, token in ipairs(tokens) do
                redis.call('DEL', ARGV[1] .. token)
            end
            redis.call('DEL', KEYS[1])
            return #tokens
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public TokenPair createTokens(Long userId) {
        String refreshToken = UUID.randomUUID().toString().replace("-", "");
        try {
            Long registered = redisTemplate.execute(REGISTER_SCRIPT,
                    List.of(REFRESH_KEY_PREFIX + refreshToken, USER_REFRESH_SET_PREFIX + userId),
                    userId.toString(), refreshToken, String.valueOf(REFRESH_TTL.toSeconds()));
            if (!Long.valueOf(1).equals(registered)) {
                throw new BusinessException(AuthResultCode.REFRESH_TOKEN_INVALID);
            }
            TokenPair tokens = loginRegisteredTokens(userId, refreshToken);
            log.info("创建Token: userId={}", userId);
            return tokens;
        } catch (RuntimeException | Error ex) {
            cleanupRefreshToken(userId, refreshToken, ex);
            throw ex;
        }
    }

    @Override
    public TokenPair refreshToken(String refreshToken) {
        String key = REFRESH_KEY_PREFIX + refreshToken;
        String userIdStr = redisTemplate.opsForValue().get(key);
        if (userIdStr == null) {
            throw new BusinessException(AuthResultCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = Long.parseLong(userIdStr);
        String newRefreshToken = UUID.randomUUID().toString().replace("-", "");
        try {
            Long rotated = redisTemplate.execute(ROTATE_SCRIPT,
                    List.of(key, REFRESH_KEY_PREFIX + newRefreshToken, USER_REFRESH_SET_PREFIX + userId),
                    userIdStr, refreshToken, newRefreshToken, String.valueOf(REFRESH_TTL.toSeconds()));
            if (!Long.valueOf(1).equals(rotated)) {
                throw new BusinessException(AuthResultCode.REFRESH_TOKEN_INVALID);
            }
            TokenPair tokens = loginRegisteredTokens(userId, newRefreshToken);
            log.info("刷新Token: userId={}", userId);
            return tokens;
        } catch (RuntimeException | Error ex) {
            cleanupRefreshToken(userId, newRefreshToken, ex);
            throw ex;
        }
    }

    private TokenPair loginRegisteredTokens(Long userId, String refreshToken) {
        boolean loggedIn = false;
        try {
            StpAppUtil.login(userId);
            loggedIn = true;
            String accessToken = StpAppUtil.getTokenValue();
            StpAppUtil.getTokenSession().set(SESSION_REFRESH_TOKEN_KEY, refreshToken);
            if (!userId.toString().equals(redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + refreshToken))) {
                throw new BusinessException(AuthResultCode.REFRESH_TOKEN_INVALID);
            }
            return new TokenPair(accessToken, refreshToken);
        } catch (RuntimeException | Error ex) {
            if (loggedIn) {
                try {
                    StpAppUtil.logout();
                } catch (RuntimeException | Error logoutEx) {
                    if (logoutEx != ex) {
                        ex.addSuppressed(logoutEx);
                    }
                }
            }
            throw ex;
        }
    }

    private void cleanupRefreshToken(Long userId, String refreshToken, Throwable cause) {
        try {
            redisTemplate.delete(REFRESH_KEY_PREFIX + refreshToken);
        } catch (RuntimeException | Error cleanupEx) {
            if (cleanupEx != cause) {
                cause.addSuppressed(cleanupEx);
            }
        }
        try {
            redisTemplate.opsForSet().remove(USER_REFRESH_SET_PREFIX + userId, refreshToken);
        } catch (RuntimeException | Error cleanupEx) {
            if (cleanupEx != cause) {
                cause.addSuppressed(cleanupEx);
            }
        }
    }

    @Override
    public void removeTokens(String refreshToken) {
        if (refreshToken != null && !refreshToken.isEmpty()) {
            String refreshKey = REFRESH_KEY_PREFIX + refreshToken;
            String userIdStr = redisTemplate.opsForValue().get(refreshKey);
            if (userIdStr != null) {
                redisTemplate.opsForSet().remove(USER_REFRESH_SET_PREFIX + userIdStr, refreshToken);
            }
            redisTemplate.delete(refreshKey);
        }
        // 登出当前设备，Sa-Token 会连带销毁其 Token-Session
        StpAppUtil.logout();
        log.info("当前设备会话已退出");
    }

    @Override
    public String getCurrentDeviceRefreshToken() {
        Object value = StpAppUtil.getTokenSession().get(SESSION_REFRESH_TOKEN_KEY);
        return value != null ? value.toString() : null;
    }

    @Override
    public void revokeUserTokens(Long userId) {
        redisTemplate.execute(REVOKE_SCRIPT, List.of(USER_REFRESH_SET_PREFIX + userId), REFRESH_KEY_PREFIX);
        StpAppUtil.logoutUser(userId);
        log.info("撤销用户全部Token: userId={}", userId);
    }

    @Override
    public Long getUserIdByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return null;
        }
        String userIdStr = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + refreshToken);
        return userIdStr != null ? Long.parseLong(userIdStr) : null;
    }
}
