package com.iot.ruleengine.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.PageResult;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.entity.Tenant;
import com.iot.ruleengine.security.RequirePermission;
import com.iot.ruleengine.service.TenantAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/tenant")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequirePermission(role = "SUPER_ADMIN")
public class TenantController {

    @Autowired
    private TenantAdminService tenantAdminService;

    @GetMapping("/list")
    public Result<PageResult<Tenant>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        Page<Tenant> page = new Page<>(pageNum, pageSize);
        Page<Tenant> result = tenantAdminService.listTenants(page, keyword);
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/{id}")
    public Result<Tenant> getById(@PathVariable Long id) {
        TenantContext.setIgnoreTenant(true);
        try {
            return Result.success(tenantRepository.selectById(id));
        } finally {
            TenantContext.setIgnoreTenant(false);
        }
    }

    @PostMapping
    public Result<Tenant> create(@RequestBody Tenant tenant) {
        return Result.success(tenantAdminService.createTenant(tenant));
    }

    @PutMapping
    public Result<Tenant> update(@RequestBody Tenant tenant) {
        return Result.success(tenantAdminService.updateTenant(tenant));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tenantAdminService.deleteTenant(id);
        return Result.success();
    }
}
