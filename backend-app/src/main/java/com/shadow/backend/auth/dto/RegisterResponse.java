package com.shadow.backend.auth.dto;

import com.shadow.backend.user.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegisterResponse {

    private UserVO user;
}
