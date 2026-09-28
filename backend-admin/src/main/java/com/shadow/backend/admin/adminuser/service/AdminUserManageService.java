package com.shadow.backend.admin.adminuser.service;

import com.shadow.backend.admin.adminuser.dto.AssignRolesRequest;
import com.shadow.backend.admin.adminuser.dto.CreateAdminUserRequest;
import com.shadow.backend.admin.adminuser.dto.UpdateAdminUserRequest;
import com.shadow.backend.admin.adminuser.vo.AdminUserManageVO;
import com.shadow.backend.common.response.PageResult;

public interface AdminUserManageService {

    /** 分页查询管理员(支持用户名模糊、角色ID、状态过滤) */
    PageResult<AdminUserManageVO> page(long current, long size, String username, Long roleId, Integer status);

    /** 管理员详情(含角色) */
    AdminUserManageVO getById(Long id);

    /** 创建管理员 */
    AdminUserManageVO create(CreateAdminUserRequest request);

    /** 修改管理员 */
    AdminUserManageVO update(Long id, UpdateAdminUserRequest request);

    /** 删除管理员 */
    void delete(Long id);

    /** 分配角色 */
    void assignRoles(Long id, AssignRolesRequest request);
}
