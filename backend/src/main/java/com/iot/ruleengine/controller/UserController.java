package com.iot.ruleengine.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iot.ruleengine.dto.PageResult;
import com.iot.ruleengine.dto.Result;
import com.iot.ruleengine.entity.SysRole;
import com.iot.ruleengine.entity.SysUser;
import com.iot.ruleengine.security.RequirePermission;
import com.iot.ruleengine.service.UserAdminService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequirePermission(role = "TENANT_ADMIN")
public class UserController {

    @Autowired
    private UserAdminService userAdminService;

    @GetMapping("/list")
    public Result<PageResult<SysUser>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword) {
        Page<SysUser> page = new Page<>(pageNum, pageSize);
        Page<SysUser> result = userAdminService.listUsers(page, keyword);
        return Result.success(PageResult.of(result));
    }

    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        SysUser user = sysUserRepository.selectById(id);
        if (user != null) {
            user.setPassword(null);
            user.setSalt(null);
            user.setRoles(sysUserRepository.selectRolesByUserId(id));
        }
        return Result.success(user);
    }

    @PostMapping
    public Result<SysUser> create(@RequestBody Map<String, Object> body) {
        SysUser user = new SysUser();
        user.setUsername((String) body.get("username"));
        user.setNickname((String) body.get("nickname"));
        user.setPassword((String) body.get("password"));
        user.setEmail((String) body.get("email"));
        user.setPhone((String) body.get("phone"));
        user.setStatus(body.get("status") != null ? ((Number) body.get("status")).intValue() : 1);
        @SuppressWarnings("unchecked")
        List<Number> roleIds = (List<Number>) body.get("roleIds");
        List<Long> rids = roleIds != null ? roleIds.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList()) : null;
        return Result.success(userAdminService.createUser(user, rids));
    }

    @PutMapping
    public Result<SysUser> update(@RequestBody Map<String, Object> body) {
        SysUser user = new SysUser();
        user.setId(body.get("id") != null ? ((Number) body.get("id")).longValue() : null);
        user.setUsername((String) body.get("username"));
        user.setNickname((String) body.get("nickname"));
        user.setPassword((String) body.get("password"));
        user.setEmail((String) body.get("email"));
        user.setPhone((String) body.get("phone"));
        user.setStatus(body.get("status") != null ? ((Number) body.get("status")).intValue() : null);
        @SuppressWarnings("unchecked")
        List<Number> roleIds = (List<Number>) body.get("roleIds");
        List<Long> rids = roleIds != null ? roleIds.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList()) : null;
        return Result.success(userAdminService.updateUser(user, rids));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userAdminService.deleteUser(id);
        return Result.success();
    }

    @Autowired
    private com.iot.ruleengine.repository.SysUserRepository sysUserRepository;
}
