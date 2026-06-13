package com.iot.ruleengine.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.entity.*;
import com.iot.ruleengine.repository.*;
import com.iot.ruleengine.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class TenantAdminService {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private SysUserRepository sysUserRepository;
    @Autowired private SysRoleRepository sysRoleRepository;
    @Autowired private SysPermissionRepository sysPermissionRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;

    public Page<Tenant> listTenants(Page<Tenant> page, String keyword) {
        TenantContext.setIgnoreTenant(true);
        try {
            QueryWrapper<Tenant> qw = new QueryWrapper<>();
            if (StringUtils.hasText(keyword)) {
                qw.and(w -> w.like("tenant_code", keyword).or().like("tenant_name", keyword));
            }
            qw.orderByDesc("create_time");
            return tenantRepository.selectPage(page, qw);
        } finally {
            TenantContext.setIgnoreTenant(false);
        }
    }

    @Transactional
    public Tenant createTenant(Tenant tenant) {
        TenantContext.setIgnoreTenant(true);
        try {
            tenant.setStatus(1);
            tenant.setCreateTime(LocalDateTime.now());
            tenant.setUpdateTime(LocalDateTime.now());
            if (tenant.getMaxUsers() == null) tenant.setMaxUsers(50);
            if (tenant.getMaxDevices() == null) tenant.setMaxDevices(500);
            if (tenant.getMaxRules() == null) tenant.setMaxRules(100);
            tenantRepository.insert(tenant);
            initTenantData(tenant);
            return tenant;
        } finally {
            TenantContext.setIgnoreTenant(false);
        }
    }

    public Tenant updateTenant(Tenant tenant) {
        TenantContext.setIgnoreTenant(true);
        try {
            tenant.setUpdateTime(LocalDateTime.now());
            tenantRepository.updateById(tenant);
            return tenant;
        } finally {
            TenantContext.setIgnoreTenant(false);
        }
    }

    public void deleteTenant(Long id) {
        TenantContext.setIgnoreTenant(true);
        try {
            tenantRepository.deleteById(id);
        } finally {
            TenantContext.setIgnoreTenant(false);
        }
    }

    private void initTenantData(Tenant tenant) {
        Long tenantId = tenant.getId();

        SysRole adminRole = new SysRole();
        adminRole.setTenantId(tenantId);
        adminRole.setRoleCode("TENANT_ADMIN");
        adminRole.setRoleName("租户管理员");
        adminRole.setRoleType(1);
        adminRole.setDataScope(2);
        adminRole.setSort(1);
        adminRole.setStatus(1);
        adminRole.setCreateTime(LocalDateTime.now());
        sysRoleRepository.insert(adminRole);

        SysRole editorRole = new SysRole();
        editorRole.setTenantId(tenantId);
        editorRole.setRoleCode("RULE_EDITOR");
        editorRole.setRoleName("规则编辑员");
        editorRole.setRoleType(2);
        editorRole.setDataScope(3);
        editorRole.setSort(2);
        editorRole.setStatus(1);
        editorRole.setCreateTime(LocalDateTime.now());
        sysRoleRepository.insert(editorRole);

        SysRole viewerRole = new SysRole();
        viewerRole.setTenantId(tenantId);
        viewerRole.setRoleCode("RULE_VIEWER");
        viewerRole.setRoleName("规则查看员");
        viewerRole.setRoleType(2);
        viewerRole.setDataScope(3);
        viewerRole.setSort(3);
        viewerRole.setStatus(1);
        viewerRole.setCreateTime(LocalDateTime.now());
        sysRoleRepository.insert(viewerRole);

        List<Long> allPermIds = new ArrayList<>();
        QueryWrapper<SysPermission> pqw = new QueryWrapper<>();
        pqw.orderByAsc("id");
        List<SysPermission> allPerms = sysPermissionRepository.selectList(pqw);
        for (SysPermission p : allPerms) {
            allPermIds.add(p.getId());
        }

        if (!allPermIds.isEmpty()) {
            for (Long pid : allPermIds) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(adminRole.getId());
                rp.setPermissionId(pid);
                rolePermissionRepository.insert(rp);
            }

            List<String> editorPerms = Arrays.asList("rule:view", "rule:edit", "rule:test", "rule:debug",
                    "device:view", "device:edit", "stats:view");
            for (SysPermission p : allPerms) {
                if (editorPerms.contains(p.getPermCode())) {
                    RolePermission rp = new RolePermission();
                    rp.setRoleId(editorRole.getId());
                    rp.setPermissionId(p.getId());
                    rolePermissionRepository.insert(rp);
                }
            }

            List<String> viewerPerms = Arrays.asList("rule:view", "device:view", "stats:view");
            for (SysPermission p : allPerms) {
                if (viewerPerms.contains(p.getPermCode())) {
                    RolePermission rp = new RolePermission();
                    rp.setRoleId(viewerRole.getId());
                    rp.setPermissionId(p.getId());
                    rolePermissionRepository.insert(rp);
                }
            }
        }

        String salt = UUID.randomUUID().toString().substring(0, 8);
        String adminPwd = DigestUtils.md5DigestAsHex(("Admin@123" + salt).getBytes());
        SysUser adminUser = new SysUser();
        adminUser.setTenantId(tenantId);
        adminUser.setUsername("admin@" + tenant.getTenantCode());
        adminUser.setPassword(adminPwd);
        adminUser.setSalt(salt);
        adminUser.setNickname("管理员");
        adminUser.setStatus(1);
        adminUser.setCreateTime(LocalDateTime.now());
        adminUser.setUpdateTime(LocalDateTime.now());
        sysUserRepository.insert(adminUser);

        UserRole ur = new UserRole();
        ur.setUserId(adminUser.getId());
        ur.setRoleId(adminRole.getId());
        userRoleRepository.insert(ur);

        log.info("租户{}初始化完成: 3个内置角色, 管理员账号{}", tenant.getTenantCode(), adminUser.getUsername());
    }
}
