package com.shadow.backend.user.service;

import com.shadow.backend.common.response.PageResult;
import com.shadow.backend.user.dto.ChangePasswordRequest;
import com.shadow.backend.user.dto.CreateUserRequest;
import com.shadow.backend.user.dto.UpdateProfileRequest;
import com.shadow.backend.user.dto.UpdateUserRequest;
import com.shadow.backend.user.dto.UserPageQuery;
import com.shadow.backend.user.entity.User;
import com.shadow.backend.user.vo.UserVO;

public interface UserService {

    UserVO create(CreateUserRequest request);

    UserVO update(Long id, UpdateUserRequest request);

    void delete(Long id);

    UserVO getById(Long id);

    PageResult<UserVO> page(UserPageQuery query);

    UserVO getByUsername(String username);

    User getByPhone(String phone);

    UserVO updateProfile(Long userId, UpdateProfileRequest request);

    void changePassword(Long userId, ChangePasswordRequest request);

    UserVO audit(Long id, Integer auditStatus, String auditRemark);
}
