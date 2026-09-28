package com.shadow.backend.admin.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shadow.backend.admin.role.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /** 查询角色关联的用户数 */
    @Select("SELECT COUNT(*) FROM sys_user_role WHERE role_id = #{roleId}")
    long countUsersByRoleId(@Param("roleId") Long roleId);

    /** 查询用户的角色编码列表 */
    @Select("SELECT DISTINCT r.code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1 AND r.deleted = 0")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
