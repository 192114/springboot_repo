package com.shadow.backend.admin.adminuser.controller;

import com.shadow.backend.admin.adminuser.dto.AssignRolesRequest;
import com.shadow.backend.admin.adminuser.dto.CreateAdminUserRequest;
import com.shadow.backend.admin.adminuser.dto.UpdateAdminUserRequest;
import com.shadow.backend.admin.adminuser.service.AdminUserManageService;
import com.shadow.backend.admin.adminuser.vo.AdminUserManageVO;
import com.shadow.backend.common.response.PageResult;
import com.shadow.backend.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户管理 Controller
 * <p>
 * 管理 sys_user 表记录（管理员账号），包含角色分配。
 * 与 AdminUserController（管理 app_user）不同。
 */
@RestController
@Validated
@RequestMapping("/api/admin/admin-users")
@RequiredArgsConstructor
public class AdminUserManageController {

    private final AdminUserManageService adminUserManageService;

    @GetMapping
    public Result<PageResult<AdminUserManageVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1") long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于1")
            @Max(value = 100, message = "每页条数不能超过100") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Integer status) {
        return Result.success(adminUserManageService.page(current, size, username, roleId, status));
    }

    @GetMapping("/{id}")
    public Result<AdminUserManageVO> getById(@PathVariable Long id) {
        return Result.success(adminUserManageService.getById(id));
    }

    @PostMapping
    public Result<AdminUserManageVO> create(@Valid @RequestBody CreateAdminUserRequest request) {
        return Result.success(adminUserManageService.create(request));
    }

    @PutMapping("/{id}")
    public Result<AdminUserManageVO> update(@PathVariable Long id, @Valid @RequestBody UpdateAdminUserRequest request) {
        return Result.success(adminUserManageService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminUserManageService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @RequestBody AssignRolesRequest request) {
        adminUserManageService.assignRoles(id, request);
        return Result.success();
    }
}
