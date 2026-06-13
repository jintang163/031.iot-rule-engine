package com.iot.ruleengine.tenant;

public class TenantContext {
    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IGNORE_TENANT = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) { TENANT_ID.set(tenantId); }
    public static Long getTenantId() { return TENANT_ID.get(); }
    public static void setUserId(Long userId) { USER_ID.set(userId); }
    public static Long getUserId() { return USER_ID.get(); }
    public static void setIgnoreTenant(boolean ignore) { IGNORE_TENANT.set(ignore); }
    public static boolean isIgnoreTenant() { return Boolean.TRUE.equals(IGNORE_TENANT.get()); }
    public static void clear() { TENANT_ID.remove(); USER_ID.remove(); IGNORE_TENANT.remove(); }
}
