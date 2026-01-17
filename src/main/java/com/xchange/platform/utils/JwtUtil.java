package com.xchange.platform.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JWT 工具类
 * 支持生成、验证、解析、刷新 Token
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * 生成密钥
     */
    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token（携带用户信息）
     * @param claims 自定义载荷（如用户ID、用户名等）
     * @return token字符串
     */
    public String generateToken(Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)                    // 自定义数据
                .issuedAt(now)                     // 签发时间
                .expiration(expiryDate)            // 过期时间
                .signWith(getKey(), Jwts.SIG.HS256) // 签名算法
                .compact();
    }

    /**
     * 从 Token 中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从 Token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token);
            return jws.getPayload();
        } catch (JwtException e) {
            log.error("Token解析失败: {}", e.getMessage());
            throw new RuntimeException("无效的Token");
        }
    }

    /**
     * 验证 Token 是否有效
     * @return true=有效, false=无效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的Token格式: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Token格式错误: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("Token签名错误: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Token参数异常: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 刷新 Token（延长有效期）
     * @param oldToken 旧token
     * @return 新token
     */
    public String refreshToken(String oldToken) {
        if (!validateToken(oldToken)) {
            throw new RuntimeException("Token无效，无法刷新");
        }

        Claims claims = parseToken(oldToken);

        Map<String, Object> newClaims = new HashMap<>(claims); // 复制所有数据

        // 移除过期时间（由 generateToken 重新设置）
        newClaims.remove(Claims.EXPIRATION);

        return generateToken(newClaims);
    }

    /**
     * 判断 Token 是否即将过期（剩余时间小于 1 小时）
     */
    public boolean isTokenExpiringSoon(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            long remainingTime = expiration.getTime() - System.currentTimeMillis();
            return remainingTime < TimeUnit.HOURS.toMillis(1);
        } catch (Exception e) {
            return true; // 解析失败视为已过期
        }
    }

    /**
     * 获取 Token 剩余有效期（毫秒）
     */
    public long getRemainingTime(String token) {
        Claims claims = parseToken(token);
        return claims.getExpiration().getTime() - System.currentTimeMillis();
    }
}