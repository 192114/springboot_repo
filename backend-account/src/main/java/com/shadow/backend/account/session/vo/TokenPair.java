package com.shadow.backend.account.session.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenPair {

    private String accessToken;
    private String refreshToken;
}
