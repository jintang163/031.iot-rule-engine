package com.iot.ruleengine.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class TenantLineInnerInterceptor extends TenantLineInnerInterceptor {

    private static final Set<String> IGNORE_TABLES = new HashSet<>(Arrays.asList(
            "sys_tenant", "sys_user", "sys_role", "sys_permission",
            "sys_user_role", "sys_role_permission"
    ));

    public TenantLineInnerInterceptor() {
        super(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.getTenantId();
                if (tenantId != null) {
                    return new LongValue(tenantId);
                }
                return new NullValue();
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                if (TenantContext.isIgnoreTenant()) {
                    return true;
                }
                return IGNORE_TABLES.contains(tableName);
            }
        });
    }
}
