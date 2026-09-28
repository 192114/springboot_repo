package com.shadow.backend.account.session.service;

import com.shadow.backend.account.session.vo.TokenPair;

public interface TokenService {

    TokenPair createTokens(Long userId);

    TokenPair refreshToken(String refreshToken);

    void removeTokens(String refreshToken);

    /** 当前请求设备对应的 Refresh Token（存储于 Token-Session），不存在返回 null */
    String getCurrentDeviceRefreshToken();

    /** 撤销指定用户的全部 Refresh Token 与会话（重置密码/禁用/删除用户后调用） */
    void revokeUserTokens(Long userId);

    /** 查询 Refresh Token 对应的用户ID（只读不消费，用于刷新前状态复核），无效返回 null */
    Long getUserIdByRefreshToken(String refreshToken);
}
