package com.atp.platform.security;

import com.atp.platform.config.AtpProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class IpWhitelistFilter extends OncePerRequestFilter {

    private final AtpProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var sec = properties.getSecurity();
        if (!sec.isIpWhitelistEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        List<String> whitelist = sec.getIpWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        if (isExempt(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String clientIp = resolveClientIp(request);
        if (isAllowed(clientIp, whitelist)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"IP_FORBIDDEN\",\"message\":\"IP 不在白名单内\"}");
    }

    private boolean isExempt(String path) {
        return path.startsWith("/health")
                || path.startsWith("/api/health")
                || path.startsWith("/api/v1/health")
                || path.equals("/")
                || path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/agent/")
                || path.startsWith("/api/v1/ci/jenkins/webhook")
                || path.startsWith("/api/v1/screen/verify");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isAllowed(String ip, List<String> whitelist) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if ("127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        for (String rule : whitelist) {
            if (rule == null || rule.isBlank()) continue;
            rule = rule.trim();
            if (rule.contains("/")) {
                if (matchCidr(ip, rule)) return true;
            } else if (rule.equals(ip)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) return false;
            InetAddress target = InetAddress.getByName(ip);
            InetAddress network = InetAddress.getByName(parts[0]);
            int prefix = Integer.parseInt(parts[1]);
            byte[] targetBytes = target.getAddress();
            byte[] networkBytes = network.getAddress();
            if (targetBytes.length != networkBytes.length) return false;
            int fullBytes = prefix / 8;
            int remBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (targetBytes[i] != networkBytes[i]) return false;
            }
            if (remBits > 0 && fullBytes < targetBytes.length) {
                int mask = 0xFF << (8 - remBits);
                if ((targetBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
