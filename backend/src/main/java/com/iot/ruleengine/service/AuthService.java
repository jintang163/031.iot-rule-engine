package com.iot.ruleengine.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.iot.ruleengine.entity.SysRole;
import com.iot.ruleengine.entity.SysUser;
import com.iot.ruleengine.entity.Tenant;
import com.iot.ruleengine.repository.SysUserRepository;
import com.iot.ruleengine.repository.TenantRepository;
import com.iot.ruleengine.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthService {

    @Autowired
    private SysUserRepository sysUserRepository;
    @Autowired
    private TenantRepository tenantRepository;

    public Map<String, Object> login(String tenantCode, String username, String password) {
        TenantContext.setIgnoreTenant(true);
        try {
            QueryWrapper<Tenant> tqw = new QueryWrapper<>();
            tqw.eq("tenant_code", tenantCode).eq("status", 1);
            Tenant tenant = tenantRepository.selectOne(tqw);
            if (tenant == null) {
                throw new RuntimeException("租户不存在或已禁用");
            }

            QueryWrapper<SysUser> uqw = new QueryWrapper<>();
            uqw.eq("username", username).eq("tenant_id", tenant.getId());
            SysUser user = sysUserRepository.selectOne(uqw);
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }
            if (user.getStatus() != null && user.getStatus() != 1) {
                throw new RuntimeException("用户已禁用");
            }

            String hashedPwd = DigestUtils.md5DigestAsHex((password + user.getSalt()).getBytes());
            if (!hashedPwd.equals(user.getPassword())) {
                throw new RuntimeException("密码错误");
            }

            user.setLastLoginTime(java.time.LocalDateTime.now());
            sysUserRepository.updateById(user);

            List<SysRole> roles = sysUserRepository.selectRolesByUserId(user.getId());
            List<String> permCodes = sysUserRepository.selectPermCodesByUserId(user.getId());
            List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
            boolean isSuperAdmin = roleCodes.contains("SUPER_ADMIN");

            String token = user.getId() + "|" + tenant.getId() + "|" + System.currentTimeMillis();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("token", token);
            result.put("userId", user.getId());
            result.put("tenantId", tenant.getId());
            result.put("tenantName", tenant.getTenantName());
            result.put("username", user.getUsername());
            result.put("nickname", user.getNickname());
            result.put("avatar", user.getAvatar());
            result.put("roles", roleCodes);
            result.put("permissions", permCodes);
            result.put("isAdmin", isSuperAdmin);
            return result;
        } finally {
            TenantContext.setIgnoreTenant(false);
        }
    }

    public SysUser getCurrentUser() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return null;
        return sysUserRepository.selectById(userId);
    }

    public List<String> getCurrentUserPermissions() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Collections.emptyList();
        return sysUserRepository.selectPermCodesByUserId(userId);
    }

    public List<SysRole> getCurrentUserRoles() {
        Long userId = TenantContext.getUserId();
        if (userId == null) return Collections.emptyList();
        return sysUserRepository.selectRolesByUserId(userId);
    }

    public void logout() {
        TenantContext.clear();
    }
}
