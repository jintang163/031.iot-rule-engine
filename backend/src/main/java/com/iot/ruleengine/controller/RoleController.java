package com.iot.ruleengine.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.PageResult;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.entity.SysPermission;
import com.iot.ruleengine.entity.SysRole;
import com.iot.ruleengine.security.RequirePermission;
import com.iot.ruleengine.service.RoleAdminService;
import com.iot.ruleengine.service.UserAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/role")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequirePermission(role = "TENANT_ADMIN")
public class RoleController {

    @Autowired
    private RoleAdminService roleAdminService;
    @Autowired
    private UserAdminService userAdminService;

    @GetMapping("/list")
    public Result<PageResult<SysRole>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        Page<SysRole> page = new Page<>(pageNum, pageSize);
        Page<SysRole> result = roleAdminService.listRoles(page, keyword);
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/all")
    public Result<List<SysRole>> all() {
        return Result.success(userAdminService.getAllRoles());
    }

    @GetMapping("/permissions/tree")
    public Result<List<SysPermission>> permissionTree() {
        return Result.success(roleAdminService.getPermissionTree());
    }

    @GetMapping("/{roleId}/permissions")
    public Result<List<Long>> rolePermissions(@PathVariable Long roleId) {
        return Result.success(roleAdminService.getRolePermissions(roleId));
    }

    @PostMapping
    public Result<SysRole> create(@RequestBody SysRole role) {
        return Result.success(roleAdminService.createRole(role));
    }

    @PutMapping
    public Result<SysRole> update(@RequestBody SysRole role) {
        return Result.success(roleAdminService.updateRole(role));
    }

    @PostMapping("/{roleId}/assign-permissions")
    public Result<Void> assignPermissions(@PathVariable Long roleId, @RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Number> pids = (List<Number>) body.get("permissionIds");
        List<Long> permissionIds = pids != null ? pids.stream().map(Number::longValue).collect(Collectors.toList()) : null;
        roleAdminService.assignPermissions(roleId, permissionIds);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleAdminService.deleteRole(id);
        return Result.success();
    }
}
