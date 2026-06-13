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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserAdminService {

    @Autowired private SysUserRepository sysUserRepository;
    @Autowired private SysRoleRepository sysRoleRepository;
    @Autowired private UserRoleRepository userRoleRepository;

    public Page<SysUser> listUsers(Page<SysUser> page, String keyword) {
        Long tenantId = TenantContext.getTenantId();
        QueryWrapper<SysUser> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId);
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("username", keyword).or().like("nickname", keyword));
        }
        qw.orderByDesc("create_time");
        Page<SysUser> result = sysUserRepository.selectPage(page, qw);
        for (SysUser u : result.getRecords()) {
            u.setPassword(null);
            u.setSalt(null);
            u.setRoles(sysUserRepository.selectRolesByUserId(u.getId()));
        }
        return result;
    }

    @Transactional
    public SysUser createUser(SysUser user, List<Long> roleIds) {
        Long tenantId = TenantContext.getTenantId();
        user.setTenantId(tenantId);
        String salt = UUID.randomUUID().toString().substring(0, 8);
        user.setSalt(salt);
        String defaultPwd = user.getPassword() != null ? user.getPassword() : "123456";
        user.setPassword(DigestUtils.md5DigestAsHex((defaultPwd + salt).getBytes()));
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        sysUserRepository.insert(user);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                UserRole ur = new UserRole();
                ur.setUserId(user.getId());
                ur.setRoleId(roleId);
                userRoleRepository.insert(ur);
            }
        }
        return user;
    }

    @Transactional
    public SysUser updateUser(SysUser user, List<Long> roleIds) {
        user.setUpdateTime(LocalDateTime.now());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            SysUser existing = sysUserRepository.selectById(user.getId());
            if (existing != null) {
                String salt = existing.getSalt();
                user.setPassword(DigestUtils.md5DigestAsHex((user.getPassword() + salt).getBytes()));
            }
        } else {
            user.setPassword(null);
        }
        sysUserRepository.updateById(user);
        if (roleIds != null) {
            QueryWrapper<UserRole> qw = new QueryWrapper<>();
            qw.eq("user_id", user.getId());
            userRoleRepository.delete(qw);
            for (Long roleId : roleIds) {
                UserRole ur = new UserRole();
                ur.setUserId(user.getId());
                ur.setRoleId(roleId);
                userRoleRepository.insert(ur);
            }
        }
        return user;
    }

    public void deleteUser(Long id) {
        sysUserRepository.deleteById(id);
        QueryWrapper<UserRole> qw = new QueryWrapper<>();
        qw.eq("user_id", id);
        userRoleRepository.delete(qw);
    }

    public List<SysRole> getAllRoles() {
        Long tenantId = TenantContext.getTenantId();
        QueryWrapper<SysRole> qw = new QueryWrapper<>();
        qw.eq("tenant_id", tenantId).eq("status", 1).orderByAsc("sort");
        return sysRoleRepository.selectList(qw);
    }
}
