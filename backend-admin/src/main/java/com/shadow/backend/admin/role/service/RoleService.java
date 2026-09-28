package com.shadow.backend.admin.role.service;

import com.shadow.backend.admin.role.dto.AssignMenusRequest;
import com.shadow.backend.admin.role.dto.CreateRoleRequest;
import com.shadow.backend.admin.role.dto.UpdateRoleRequest;
import com.shadow.backend.admin.role.vo.RoleVO;
import com.shadow.backend.common.response.PageResult;

public interface RoleService {

    /** 分页查询角色(支持名称模糊、状态过滤) */
    PageResult<RoleVO> page(long current, long size, String name, Integer status);

    /** 全部角色(下拉用) */
    java.util.List<RoleVO> listAll();

    /** 角色详情(含已选菜单ID) */
    RoleVO getById(Long id);

    /** 创建角色 */
    RoleVO create(CreateRoleRequest request);

    /** 修改角色 */
    RoleVO update(Long id, UpdateRoleRequest request);

    /** 删除角色 */
    void delete(Long id);

    /** 分配菜单权限 */
    void assignMenus(Long id, AssignMenusRequest request);
}
