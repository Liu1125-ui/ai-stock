package com.aistock.gateway.statistics;
import com.aistock.gateway.security.AdminReadAccess;
import jakarta.servlet.http.HttpServletRequest;
/** Compatibility wrapper for the existing statistics entry point. */
public final class StatisticsAdminAccess {
 private final AdminReadAccess delegate;
 public StatisticsAdminAccess(StatisticsIdentityClient client,String token){delegate=new AdminReadAccess(client,token);}
 public void requireAdmin(HttpServletRequest request){delegate.requireAdmin(request);}
}
