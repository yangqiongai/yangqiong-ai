/*
 * Copyright (C) 2026 yangqiong
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3 of the License
 * only ("AGPL-3.0-only") and not any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.yangqiongai.ai.platform.auth.service;

import com.yangqiongai.ai.platform.auth.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 交换服务
 * <p>
 * 将访问令牌校验后交换为系统内部 JWT，并支持 JWT 校验与刷新。
 * OIDC 校验器为可选扩展点，社区默认不注册，商业版注入 JWKS 校验实现。
 * </p>
 * @author yangqiong
 */
public class TokenExchangeService {

    private static final Logger log = LoggerFactory.getLogger(TokenExchangeService.class);

    /**
     * 签名密钥最小长度（字节）
     */
    private static final int MIN_SECRET_LENGTH = 32;

    /**
     * claim 键名-提供方
     */
    public static final String CLAIM_PROVIDER = "provider";

    /**
     * claim 键名-显示名称
     */
    public static final String CLAIM_DISPLAY_NAME = "displayName";

    /**
     * claim 键名-主题
     */
    public static final String CLAIM_SUBJECT = "sub";

    /**
     * JWT 签名密钥
     */
    private final SecretKey signingKey;

    /**
     * JWT 有效期
     */
    private final long expirationMs;

    /**
     * OIDC 令牌校验器
     */
    @Autowired(required = false)
    private OidcTokenValidator oidcTokenValidator;

    /**
     * 已登出令牌黑名单（token -> 过期时间戳），进程内实现，单实例部署可用
     */
    private final Map<String, Long> revokedTokens = new ConcurrentHashMap<>();

    /**
     * 黑名单最大容量，超出后清理已过期条目
     */
    private static final int REVOKE_MAX_SIZE = 10_000;

    public TokenExchangeService(AuthProperties authProperties) {
        AuthProperties.JwtConfig jwtConfig = authProperties.getJwt();
        String secret = jwtConfig.getSecret();
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("ai.auth.jwt.secret 未配置，生产环境必须外部化 JWT 签名密钥");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("ai.auth.jwt.secret 长度不足，至少需要 " + MIN_SECRET_LENGTH + " 字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = jwtConfig.getExpirationMs();
    }

    /**
     * 校验 OAuth2 访问令牌并交换为系统 JWT
     * @param oauth2AccessToken
     * @return
     */
    public String exchangeToken(String oauth2AccessToken) {
        if (oauth2AccessToken == null || oauth2AccessToken.isEmpty()) {
            throw new IllegalArgumentException("OAuth2 访问令牌不能为空");
        }
        if (oidcTokenValidator == null) {
            throw new IllegalStateException("未配置 OIDC 令牌校验器，无法校验访问令牌");
        }
        String subject = oidcTokenValidator.resolveSubject(stripBearer(oauth2AccessToken));
        return buildToken(subject, "oidc");
    }

    /**
     * 直接签发系统 JWT（本地登录、租户登录等内部账号源使用）
     * @param subject
     * @param provider
     * @return
     */
    public String issueToken(String subject, String provider) {
        if (subject == null || subject.isEmpty()) {
            throw new IllegalArgumentException("签发主体不能为空");
        }
        return buildToken(subject, provider);
    }

    /**
     * 刷新令牌，基于原令牌中的用户标识重新签发
     * @param jwtToken
     * @return
     */
    public String refreshToken(String jwtToken) {
        Claims claims = parseToken(jwtToken);
        if (claims == null) {
            throw new IllegalArgumentException("令牌无效或已过期");
        }
        String subject = claims.getSubject();
        String provider = claims.get(CLAIM_PROVIDER, String.class);
        return buildToken(subject, provider);
    }

    /**
     * 校验 JWT 是否合法且未过期
     * @param jwtToken
     * @return
     */
    public boolean validateToken(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            return false;
        }
        if (isRevoked(jwtToken)) {
            return false;
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(jwtToken);
            Claims claims = jws.getPayload();
            Date expiration = claims.getExpiration();
            return expiration != null && !expiration.before(new Date());
        } catch (Exception e) {
            log.debug("JWT校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 登出，将令牌加入黑名单直至其自然过期
     * @param jwtToken
     * @return
     */
    public boolean logout(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            return false;
        }
        Claims claims = parseToken(jwtToken);
        if (claims == null || claims.getExpiration() == null) {
            return false;
        }
        revokedTokens.put(jwtToken, claims.getExpiration().getTime());
        if (revokedTokens.size() > REVOKE_MAX_SIZE) {
            clearExpiredRevoked();
        }
        return true;
    }

    /**
     * 令牌是否已被登出吊销
     * @param jwtToken
     * @return
     */
    public boolean isRevoked(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            return false;
        }
        Long expiry = revokedTokens.get(jwtToken);
        if (expiry == null) {
            return false;
        }
        if (expiry < System.currentTimeMillis()) {
            revokedTokens.remove(jwtToken);
            return false;
        }
        return true;
    }

    /**
     * 清理已过期的黑名单条目，避免内存膨胀
     */
    private void clearExpiredRevoked() {
        long now = System.currentTimeMillis();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    /**
     * 解析 JWT 返回 Claims，失败返回 null
     * @param jwtToken
     * @return
     */
    public Claims parseToken(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(jwtToken)
                    .getPayload();
        } catch (Exception e) {
            log.debug("JWT解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建并签名 JWT
     * @param subject
     * @param provider
     * @return
     */
    private String buildToken(String subject, String provider) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_PROVIDER, provider);
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 去除 Bearer 前缀
     * @param token
     * @return
     */
    private String stripBearer(String token) {
        if (token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}