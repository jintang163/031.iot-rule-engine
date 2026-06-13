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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoleAdminService {

    @Autowired private SysRoleRepository sysRoleRepository;
    @Autowired private SysPermissionRepository sysPermissionRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;

    public Page<SysRole> listRoles(Page<SysRole> page, String keyword) {
        Long tenantId = TenantContext.getTenantId();
        QueryWrapper<SysRole> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("role_code", keyword).or().like("role_name", keyword));
        }
        qw.orderByAsc("sort");
        return sysRoleRepository.selectPage(page, qw);
    }

    public SysRole createRole(SysRole role) {
        Long tenantId = TenantContext.getTenantId();
        role.setTenantId(tenantId);
        role.setRoleType(2);
        role.setStatus(1);
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        sysRoleRepository.insert(role);
        return role;
    }

    public SysRole updateRole(SysRole role) {
        role.setUpdateTime(LocalDateTime.now());
        sysRoleRepository.updateById(role);
        return role;
    }

    public void deleteRole(Long id) {
        sysRoleRepository.deleteById(id);
        QueryWrapper<RolePermission> qw = new QueryWrapper<>();
        qw.eq("role_id", id);
        rolePermissionRepository.delete(qw);
    }

    public List<SysPermission> getPermissionTree() {
        QueryWrapper<SysPermission> qw = new QueryWrapper<>();
        qw.eq("status", 1).orderByAsc("sort");
        List<SysPermission> all = sysPermissionRepository.selectList(qw);
        return buildTree(all, 0L);
    }

    private List<SysPermission> buildTree(List<SysPermission> all, Long parentId) {
        return all.stream()
                .filter(p -> parentId.equals(p.getParentId()))
                .peek(p -> p.setChildren(buildTree(all, p.getId())))
                .collect(Collectors.toList());
    }

    public List<Long> getRolePermissions(Long roleId) {
        QueryWrapper<RolePermission> qw = new QueryWrapper<>();
        qw.eq("role_id", roleId);
        List<RolePermission> list = rolePermissionRepository.selectList(qw);
        return list.stream().map(RolePermission::getPermissionId).collect(Collectors.toList());
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        QueryWrapper<RolePermission> qw = new QueryWrapper<>();
        qw.eq("role_id", roleId);
        rolePermissionRepository.delete(qw);
        if (permissionIds != null) {
            for (Long pid : permissionIds) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(pid);
                rolePermissionRepository.insert(rp);
            }
        }
    }
}
