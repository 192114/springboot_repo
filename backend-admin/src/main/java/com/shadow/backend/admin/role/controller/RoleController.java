package com.shadow.backend.admin.role.controller;

import com.shadow.backend.admin.role.dto.AssignMenusRequest;
import com.shadow.backend.admin.role.dto.CreateRoleRequest;
import com.shadow.backend.admin.role.dto.UpdateRoleRequest;
import com.shadow.backend.admin.role.service.RoleService;
import com.shadow.backend.admin.role.vo.RoleVO;
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

import java.util.List;

/**
 * 角色管理 Controller
 */
@RestController
@Validated
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public Result<PageResult<RoleVO>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1") long current,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页条数不能小于1")
            @Max(value = 100, message = "每页条数不能超过100") long size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        return Result.success(roleService.page(current, size, name, status));
    }

    @GetMapping("/all")
    public Result<List<RoleVO>> listAll() {
        return Result.success(roleService.listAll());
    }

    @GetMapping("/{id}")
    public Result<RoleVO> getById(@PathVariable Long id) {
        return Result.success(roleService.getById(id));
    }

    @PostMapping
    public Result<RoleVO> create(@Valid @RequestBody CreateRoleRequest request) {
        return Result.success(roleService.create(request));
    }

    @PutMapping("/{id}")
    public Result<RoleVO> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return Result.success(roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/menus")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody AssignMenusRequest request) {
        roleService.assignMenus(id, request);
        return Result.success();
    }
}
