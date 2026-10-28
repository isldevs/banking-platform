package com.demo.auth.infrastructure.security;

import com.demo.auth.application.port.out.TokenService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class TokenServiceImpl implements TokenService {

  private final JwtEncoder encoder;
  private final String issuer;
  private final String audience;
  private final Duration accessTokenTtl;

  @Autowired
  public TokenServiceImpl(
      @Value("${security.jwt.keystore-path}") String keystorePath,
      @Value("${security.jwt.keystore-password}") String keystorePassword,
      @Value("${security.jwt.keystore-alias:jwt-signing}") String keyAlias,
      @Value("${security.jwt.issuer}") String issuer,
      @Value("${security.jwt.audience}") String audience,
      @Value("${security.jwt.access-token-ttl:PT15M}") Duration accessTokenTtl) {
    this.issuer = issuer;
    this.audience = audience;
    this.accessTokenTtl = accessTokenTtl;
    this.encoder = createEncoder(keystorePath, keystorePassword, keyAlias);
  }

  private static JwtEncoder createEncoder(
      String keystorePath, String keystorePassword, String keyAlias) {
    try {
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      try (var input = Files.newInputStream(Path.of(keystorePath))) {
        keyStore.load(input, keystorePassword.toCharArray());
      }

      if (!keyStore.containsAlias(keyAlias) || !keyStore.isKeyEntry(keyAlias)) {
        throw new IllegalStateException(
            "Configured JWT signing alias is missing or is not a key entry");
      }

      RSAPrivateKey privateKey =
          (RSAPrivateKey) keyStore.getKey(keyAlias, keystorePassword.toCharArray());
      var certificate = keyStore.getCertificate(keyAlias);
      if (certificate == null || !(certificate.getPublicKey() instanceof RSAPublicKey publicKey)) {
        throw new IllegalStateException(
            "Configured JWT keystore entry does not contain an RSA certificate");
      }

      RSAKey signingKey =
          new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("demo-rsa-1").build();
      return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to read the configured JWT keystore", exception);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException(
          "Unable to initialize JWT signing from the configured keystore", exception);
    }
  }

  @Override
  public String access(UUID id, String username, List<String> permissions, List<String> roles) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(List.of(audience))
            .subject(id.toString())
            .issuedAt(now)
            .expiresAt(now.plus(accessTokenTtl))
            .claim("username", username)
            .claim("permissions", permissions)
            .claim("roles", roles)
            .build();
    try {
      return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    } catch (JwtEncodingException exception) {
      throw new IllegalStateException("Unable to sign JWT access token", exception);
    }
  }
}
