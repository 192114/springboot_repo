package com.shadow.backend.admin.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shadow.backend.admin.auth.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    // 逻辑删除账号仍占用唯一用户名，初始化时必须一并判断。
    @Select("SELECT EXISTS(SELECT 1 FROM sys_user WHERE username = #{username})")
    boolean existsByUsernameIncludingDeleted(@Param("username") String username);
}
