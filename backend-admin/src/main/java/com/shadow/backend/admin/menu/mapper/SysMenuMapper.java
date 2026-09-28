package com.shadow.backend.admin.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shadow.backend.admin.menu.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /** 查询用户拥有的菜单(通过角色关联) */
    @Select("SELECT DISTINCT m.* FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN sys_role r ON r.id = rm.role_id AND r.status = 1 AND r.deleted = 0 " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND m.status = 1 AND m.deleted = 0 " +
            "ORDER BY m.sort_order")
    List<SysMenu> selectMenusByUserId(@Param("userId") Long userId);

    /** 查询用户拥有的权限标识列表 */
    @Select("SELECT DISTINCT m.permission FROM sys_menu m " +
            "INNER JOIN sys_role_menu rm ON m.id = rm.menu_id " +
            "INNER JOIN sys_role r ON r.id = rm.role_id AND r.status = 1 AND r.deleted = 0 " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND m.permission IS NOT NULL AND m.permission != '' " +
            "AND m.status = 1 AND m.deleted = 0")
    List<String> selectPermissionsByUserId(@Param("userId") Long userId);
}
