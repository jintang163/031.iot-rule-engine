package com.iot.ruleengine.tenant;

import com.iot.ruleengine.repository.SysUserRepository;
import com.iot.ruleengine.entity.SysUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Autowired
    private SysUserRepository sysUserRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String[] parts = token.split("\\|");
                if (parts.length >= 2) {
                    Long userId = Long.parseLong(parts[0]);
                    Long tenantId = Long.parseLong(parts[1]);
                    TenantContext.setTenantId(tenantId);
                    TenantContext.setUserId(userId);
                }
            } catch (Exception e) {
                log.warn("解析Token失败: {}", e.getMessage());
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
