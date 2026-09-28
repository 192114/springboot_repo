package com.shadow.backend.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RefreshTokenResponse {

    private String accessToken;
    private String refreshToken;
}
