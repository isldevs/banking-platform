package com.demo.auth.service.impl;

import com.demo.auth.service.TokenService;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TokenServiceImpl implements TokenService {

    private final JwtEncoder encoder;
    private final String issuer;

    @Autowired
    public TokenServiceImpl(
            @Value("${security.jwt.keystore-path}") String keystorePath,
            @Value("${security.jwt.keystore-password}") String keystorePassword,
            @Value("${security.jwt.keystore-alias:jwt-signing}") String keyAlias,
            @Value("${security.jwt.issuer}") String issuer) throws Exception {
        this.issuer = issuer;

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of(keystorePath))) {
            keyStore.load(input, keystorePassword.toCharArray());
        }

        if (!keyStore.containsAlias(keyAlias) || !keyStore.isKeyEntry(keyAlias)) {
            throw new IllegalStateException("Configured JWT signing alias is missing or is not a key entry");
        }

        RSAPrivateKey privateKey = (RSAPrivateKey) keyStore.getKey(keyAlias, keystorePassword.toCharArray());
        var certificate = keyStore.getCertificate(keyAlias);
        if (certificate == null || !(certificate.getPublicKey() instanceof RSAPublicKey publicKey)) {
            throw new IllegalStateException("Configured JWT keystore entry does not contain an RSA certificate");
        }

        RSAKey signingKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("demo-rsa-1")
                .build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(signingKey)));
    }

    @Override
    public String access(UUID id, String username, List<String> permissions, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of("api"))
                .subject(id.toString()).issuedAt(now).expiresAt(now.plusSeconds(900))
                .claim("username", username).claim("permissions", permissions).claim("roles", roles).build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
