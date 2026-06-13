package com.iot.ruleengine.security;

import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.entity.SysRole;
import com.iot.ruleengine.entity.SysUser;
import com.iot.ruleengine.repository.SysUserRepository;
import com.iot.ruleengine.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Aspect
@Component
public class PermissionAspect {

    @Autowired
    private SysUserRepository sysUserRepository;

    @Around("@annotation(com.iot.ruleengine.security.RequirePermission) || @within(com.iot.ruleengine.security.RequirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        Long userId = TenantContext.getUserId();
        if (userId == null) {
            return Result.fail("未登录或Token已过期");
        }

        RequirePermission annotation = null;
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        annotation = signature.getMethod().getAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = joinPoint.getTarget().getClass().getAnnotation(RequirePermission.class);
        }
        if (annotation == null) {
            return joinPoint.proceed();
        }

        SysUser user = sysUserRepository.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        List<SysRole> roles = sysUserRepository.selectRolesByUserId(userId);
        boolean isSuperAdmin = roles.stream().anyMatch(r -> "SUPER_ADMIN".equals(r.getRoleCode()));
        boolean isTenantAdmin = roles.stream().anyMatch(r -> "TENANT_ADMIN".equals(r.getRoleCode()));

        String requiredRole = annotation.role();
        if (StringUtils.hasText(requiredRole)) {
            if (isSuperAdmin) return joinPoint.proceed();
            if ("TENANT_ADMIN".equals(requiredRole) && (isTenantAdmin || isSuperAdmin)) return joinPoint.proceed();
            if (!roles.stream().anyMatch(r -> requiredRole.equals(r.getRoleCode()))) {
                return Result.fail("没有操作权限，需要角色: " + requiredRole);
            }
        }

        String requiredPerm = annotation.value();
        if (StringUtils.hasText(requiredPerm)) {
            if (isSuperAdmin || isTenantAdmin) return joinPoint.proceed();
            List<String> perms = sysUserRepository.selectPermCodesByUserId(userId);
            if (!perms.contains(requiredPerm) && !perms.contains("*")) {
                return Result.fail("没有操作权限，需要: " + requiredPerm);
            }
        }

        return joinPoint.proceed();
    }
}
