package com.shadow.backend.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shadow.backend.common.response.ResultCode;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shadow.backend.account.session.service.TokenService;
import com.shadow.backend.common.exception.BusinessException;
import com.shadow.backend.common.response.PageResult;
import com.shadow.backend.common.util.PasswordUtil;
import com.shadow.backend.user.dto.ChangePasswordRequest;
import com.shadow.backend.user.dto.CreateUserRequest;
import com.shadow.backend.user.dto.UpdateProfileRequest;
import com.shadow.backend.user.dto.UpdateUserRequest;
import com.shadow.backend.user.dto.UserPageQuery;
import com.shadow.backend.user.entity.User;
import com.shadow.backend.user.mapper.UserMapper;
import com.shadow.backend.user.response.UserResultCode;
import com.shadow.backend.user.service.UserService;
import com.shadow.backend.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordUtil passwordUtil;
    private final TokenService tokenService;

    @Override
    public UserVO create(CreateUserRequest request) {
        if (getEntityByUsername(request.getUsername()) != null) {
            throw new BusinessException(UserResultCode.USERNAME_EXIST);
        }

        User user = new User();
        user.setPhone(request.getPhone());
        user.setUsername(request.getUsername());
        user.setPassword(passwordUtil.hash(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setStatus(1);
        userMapper.insert(user);
        return toVO(user);
    }

    @Override
    @Transactional
    public UserVO update(Long id, UpdateUserRequest request) {
        User user = getEntityById(id);
        boolean hasNickname = StringUtils.hasText(request.getNickname());
        boolean hasEmail = StringUtils.hasText(request.getEmail());
        if (!hasNickname && !hasEmail && request.getStatus() == null) {
            return toVO(user);
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(hasNickname, User::getNickname, request.getNickname())
                .set(hasEmail, User::getEmail, request.getEmail())
                .set(request.getStatus() != null, User::getStatus, request.getStatus())
                .set(User::getUpdateTime, LocalDateTime.now()));
        if (Integer.valueOf(0).equals(request.getStatus())) {
            tokenService.revokeUserTokens(id);
        }
        return getById(id);
    }

    @Override
    public void delete(Long id) {
        getEntityById(id);
        userMapper.deleteById(id);
        tokenService.revokeUserTokens(id);
    }

    @Override
    public UserVO getById(Long id) {
        return toVO(getEntityById(id));
    }

    @Override
    public PageResult<UserVO> page(UserPageQuery query) {
        Page<User> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .like(StringUtils.hasText(query.getUsername()), User::getUsername, query.getUsername())
                .eq(query.getAuditStatus() != null, User::getAuditStatus, query.getAuditStatus())
                .orderByDesc(User::getId);
        Page<User> userPage = userMapper.selectPage(page, wrapper);
        Page<UserVO> resultPage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        resultPage.setRecords(userPage.getRecords().stream().map(this::toVO).toList());
        return PageResult.of(resultPage);
    }

    @Override
    public UserVO getByUsername(String username) {
        User user = getEntityByUsername(username);
        return user == null ? null : toVO(user);
    }

    @Override
    public User getByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
    }

    @Override
    @Transactional
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getEntityById(userId);
        boolean hasNickname = StringUtils.hasText(request.getNickname());
        boolean hasEmail = StringUtils.hasText(request.getEmail());
        boolean hasAvatar = StringUtils.hasText(request.getAvatar());
        if (!hasNickname && !hasEmail && !hasAvatar) {
            return toVO(user);
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(hasNickname, User::getNickname, request.getNickname())
                .set(hasEmail, User::getEmail, request.getEmail())
                .set(hasAvatar, User::getAvatar, request.getAvatar())
                .set(User::getUpdateTime, LocalDateTime.now()));
        return getById(userId);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getEntityById(userId);
        if (!passwordUtil.verify(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(UserResultCode.OLD_PASSWORD_INCORRECT);
        }
        user.setPassword(passwordUtil.hash(request.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public UserVO audit(Long id, Integer auditStatus, String auditRemark) {
        if (!Integer.valueOf(1).equals(auditStatus) && !Integer.valueOf(2).equals(auditStatus)) {
            throw new BusinessException(ResultCode.BAD_REQUEST);
        }
        getEntityById(id);
        LocalDateTime now = LocalDateTime.now();
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .eq(User::getAuditStatus, 0)
                .set(User::getAuditStatus, auditStatus)
                .set(User::getAuditRemark, auditRemark)
                .set(User::getAuditTime, now)
                .set(User::getUpdateTime, now));
        if (updated == 0) {
            throw new BusinessException(UserResultCode.USER_ALREADY_AUDITED);
        }
        return getById(id);
    }

    private User getEntityById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(UserResultCode.USER_NOT_FOUND);
        }
        return user;
    }

    private User getEntityByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setPhone(user.getPhone());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setStatus(user.getStatus());
        vo.setAuditStatus(user.getAuditStatus());
        vo.setAuditRemark(user.getAuditRemark());
        vo.setAuditTime(user.getAuditTime());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}
