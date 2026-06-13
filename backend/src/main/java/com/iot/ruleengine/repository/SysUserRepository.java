package com.iot.ruleengine.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iot.ruleengine.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysUserRepository extends BaseMapper<SysUser> {
    @Select("SELECT r.* FROM sys_role r INNER JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<com.iot.ruleengine.entity.SysRole> selectRolesByUserId(Long userId);

    @Select("SELECT DISTINCT p.perm_code FROM sys_permission p INNER JOIN sys_role_permission rp ON p.id = rp.permission_id INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> selectPermCodesByUserId(Long userId);
}
