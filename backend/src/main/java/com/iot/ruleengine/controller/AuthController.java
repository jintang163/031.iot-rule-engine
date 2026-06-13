package com.iot.ruleengine.controller;

import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.entity.SysRole;
import com.iot.ruleengine.entity.SysUser;
import com.iot.ruleengine.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String tenantCode = body.get("tenantCode");
        String username = body.get("username");
        String password = body.get("password");
        if (tenantCode == null || username == null || password == null) {
            return Result.fail("参数不完整");
        }
        try {
            Map<String, Object> result = authService.login(tenantCode, username, password);
            return Result.success(result);
        } catch (Exception e) {
            log.warn("登录失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        SysUser user = authService.getCurrentUser();
        if (user == null) {
            return Result.fail("未登录");
        }
        List<SysRole> roles = authService.getCurrentUserRoles();
        List<String> perms = authService.getCurrentUserPermissions();
        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
        boolean isAdmin = roleCodes.contains("SUPER_ADMIN");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("userId", user.getId());
        info.put("tenantId", user.getTenantId());
        info.put("username", user.getUsername());
        info.put("nickname", user.getNickname());
        info.put("avatar", user.getAvatar());
        info.put("roles", roleCodes);
        info.put("permissions", perms);
        info.put("isAdmin", isAdmin);
        return Result.success(info);
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }
}
