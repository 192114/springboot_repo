package com.shadow.backend.account.response;

import com.shadow.backend.common.response.IResultCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuthResultCode implements IResultCode {

    PHONE_NOT_REGISTERED(10010, "手机号未注册"),
    PHONE_ALREADY_REGISTERED(10011, "手机号已注册"),
    SMS_CODE_NOT_FOUND(10012, "验证码已过期，请重新获取"),
    SMS_CODE_INVALID(10013, "验证码错误"),
    SMS_CODE_SEND_TOO_FREQUENT(10014, "验证码发送过于频繁，请稍后再试"),
    SMS_SCENE_INVALID(10015, "无效的验证码场景"),
    REFRESH_TOKEN_INVALID(10016, "刷新令牌无效或已过期"),
    USER_AUDIT_PENDING(10017, "账号审核中，请耐心等待管理员审核"),
    USER_AUDIT_REJECTED(10018, "注册审核未通过"),
    RESUBMIT_NOT_REJECTED(10020, "当前状态不允许重新提交，仅审核驳回后可操作"),
    LOGIN_LOCKED(10021, "登录失败次数过多，请15分钟后再试"),
    SMS_CODE_EXCEED_ATTEMPTS(10022, "验证码错误次数过多，请重新获取"),
    SMS_CODE_DAILY_LIMIT(10023, "今日验证码发送次数已达上限"),
    AUDIT_QUERY_TOO_FREQUENT(10024, "查询过于频繁，请稍后再试"),
    SMS_NOT_CONFIGURED(10025, "短信服务未配置");

    private final int code;
    private final String msg;
}
