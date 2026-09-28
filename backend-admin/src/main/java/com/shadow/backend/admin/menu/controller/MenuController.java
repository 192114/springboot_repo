package com.shadow.backend.admin.menu.controller;

import com.shadow.backend.admin.menu.dto.CreateMenuRequest;
import com.shadow.backend.admin.menu.dto.UpdateMenuRequest;
import com.shadow.backend.admin.menu.service.MenuService;
import com.shadow.backend.admin.menu.vo.MenuTreeVO;
import com.shadow.backend.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单管理 Controller
 */
@RestController
@RequestMapping("/api/admin/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** 获取当前用户菜单树(侧边栏) */
    @GetMapping("/tree")
    public Result<List<MenuTreeVO>> tree() {
        return Result.success(menuService.getCurrentUserMenuTree());
    }

    /** 获取完整菜单树(管理用) */
    @GetMapping("/all")
    public Result<List<MenuTreeVO>> all() {
        return Result.success(menuService.getAllMenuTree());
    }

    @PostMapping
    public Result<MenuTreeVO> create(@Valid @RequestBody CreateMenuRequest request) {
        return Result.success(menuService.create(request));
    }

    @PutMapping("/{id}")
    public Result<MenuTreeVO> update(@PathVariable Long id, @Valid @RequestBody UpdateMenuRequest request) {
        return Result.success(menuService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }
}
