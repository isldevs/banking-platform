package com.demo.auth;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final String issuer;

    public TokenService(@Value("${security.jwt.private-key-path}") String privatePath,
                        @Value("${security.jwt.public-key-path}") String publicPath,
                        @Value("${security.jwt.issuer}") String issuer) throws Exception {
        this.issuer = issuer;
        String privatePem = Files.readString(Path.of(privatePath)).replaceAll("-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\\s", "");
        String publicPem = Files.readString(Path.of(publicPath)).replaceAll("-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\\s", "");
        KeyFactory f = KeyFactory.getInstance("RSA");
        RSAPrivateKey priv = (RSAPrivateKey) f.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privatePem)));
        RSAPublicKey pub = (RSAPublicKey) f.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicPem)));
        RSAKey jwk = new RSAKey.Builder(pub).privateKey(priv).keyID("demo-rsa-1").build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(jwk)));
    }

    public String access(UUID id, String username, List<String> permissions, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of("api"))
                .subject(id.toString()).issuedAt(now).expiresAt(now.plusSeconds(900))
                .claim("username", username).claim("permissions", permissions).claim("roles", roles).build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
