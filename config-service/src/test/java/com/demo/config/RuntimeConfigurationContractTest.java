package com.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Checks the configuration that real services consume, without connecting to external systems. */
class RuntimeConfigurationContractTest {
  private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
  private static final List<String> CLIENTS =
      List.of("auth", "customer", "account", "transfer", "notification", "audit", "gateway");
  private static final List<String> PROFILES = List.of("dev", "qa", "stage", "prod");
  private static final Pattern VALUE = Pattern.compile("@Value\\(\"([^\"]+)\"\\)");

  @Test
  void documentedVariablesResolveEveryProfilesRequiredProperties() throws Exception {
    for (String profile : PROFILES) {
      Map<String, Object> variables = documentedVariables();
      if (!"dev".equals(profile)) variables.keySet().removeIf(key -> key.startsWith("DEMO_"));
      for (String service : CLIENTS) {
        verifyRuntime(service, profile, environment(service, profile, variables));
      }
    }
  }

  @Test
  void composeActuallyForwardsTheVariablesNeededByEachContainer() throws Exception {
    Map<String, Object> compose = compose();
    Map<String, Object> services = mapping(compose.get("services"));
    StandardEnvironment interpolation = cleanEnvironment(documentedVariables());
    String expectedIssuer = null;
    String expectedAudience = null;
    for (String service : CLIENTS) {
      Map<String, Object> definition = mapping(services.get(service + "-service"));
      Map<String, Object> forwarded = new LinkedHashMap<>();
      mapping(definition.get("environment"))
          .forEach(
              (key, value) ->
                  forwarded.put(key, interpolation.resolveRequiredPlaceholders(value.toString())));
      StandardEnvironment environment = environment(service, "dev", forwarded);
      verifyRuntime(service, "dev", environment);
      String issuer = environment.getRequiredProperty("security.jwt.issuer");
      String audience = environment.getRequiredProperty("security.jwt.audience");
      if (expectedIssuer == null) {
        expectedIssuer = issuer;
        expectedAudience = audience;
      }
      assertThat(issuer).as(service + " issuer must agree with auth").isEqualTo(expectedIssuer);
      assertThat(audience)
          .as(service + " audience must agree with auth")
          .isEqualTo(expectedAudience);
    }
  }

  @Test
  void missingContainerFundingIdentifierIsRejected() throws Exception {
    Map<String, Object> variables = documentedVariables();
    variables.remove("SETTLEMENT_ACCOUNT_BANK_TRANSFER_USD_ID");
    assertThatThrownBy(
            () -> verifyRuntime("account", "dev", environment("account", "dev", variables)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SETTLEMENT_ACCOUNT_BANK_TRANSFER_USD_ID");
  }

  @Test
  void missingNonDevDatabasePasswordIsRejected() throws Exception {
    Map<String, Object> variables = documentedVariables();
    variables.remove("DATASOURCE_PASSWORD");
    assertThatThrownBy(
            () -> verifyRuntime("account", "prod", environment("account", "prod", variables)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DATASOURCE_PASSWORD");
  }

  private static void verifyRuntime(String service, String profile, StandardEnvironment environment)
      throws IOException {
    Path javaRoot = ROOT.resolve(service + "-service/src/main/java");
    try (var paths = Files.walk(javaRoot)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String source = Files.readString(path);
        if (!"dev".equals(profile) && source.contains("@Profile(\"dev\")")) continue;
        var matcher = VALUE.matcher(source);
        while (matcher.find()) environment.resolveRequiredPlaceholders(matcher.group(1));
      }
    }
    if (!"gateway".equals(service)) {
      for (String property : List.of("url", "username", "password")) {
        assertThat(environment.getRequiredProperty("spring.datasource." + property))
            .as(service + "/" + profile + " datasource " + property)
            .isNotBlank();
      }
      assertThat(environment.getRequiredProperty("security.jwt.public-key-path")).isNotBlank();
    }
    if ("account".equals(service)) {
      for (String channel : List.of("BANK_TRANSFER", "CASH", "CARD", "CHEQUE", "DIRECT_DEBIT")) {
        for (String currency : List.of("USD", "KHR")) {
          environment.getRequiredProperty(
              "account.settlement.source-account-ids." + channel + "." + currency, UUID.class);
        }
      }
      for (String currency : List.of("USD", "KHR")) {
        environment.getRequiredProperty(
            "account.funding.source-account-ids." + currency, UUID.class);
      }
    }
  }

  private static StandardEnvironment environment(
      String service, String profile, Map<String, Object> variables) throws IOException {
    StandardEnvironment environment = cleanEnvironment(variables);
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    for (Path path :
        List.of(
            ROOT.resolve(
                "config-service/src/main/resources/config/"
                    + profile
                    + "/"
                    + service
                    + "-service.yml"),
            ROOT.resolve(service + "-service/src/main/resources/application.yml"))) {
      for (PropertySource<?> source : loader.load(path.toString(), new FileSystemResource(path))) {
        environment.getPropertySources().addLast(source);
      }
    }
    return environment;
  }

  private static StandardEnvironment cleanEnvironment(Map<String, Object> variables) {
    StandardEnvironment environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .addFirst(new SystemEnvironmentPropertySource("deployment", variables));
    environment
        .getPropertySources()
        .addLast(new MapPropertySource("jvm", Map.of("user.dir", ROOT.toString())));
    return environment;
  }

  private static Map<String, Object> documentedVariables() throws IOException {
    Map<String, Object> variables = new LinkedHashMap<>();
    for (String line : Files.readAllLines(ROOT.resolve("infrastructure/.env.example"))) {
      if (line.isBlank() || line.startsWith("#")) continue;
      String[] pair = line.split("=", 2);
      variables.put(pair[0], pair[1]);
    }
    variables.put("SECURITY_JWT_PUBLIC_KEY_PATH", "/runtime/secrets/jwt-public.pem");
    variables.put("SECURITY_JWT_KEYSTORE_PATH", "/runtime/secrets/jwt-signing.p12");
    variables.put("DATASOURCE_URL", "jdbc:postgresql://database:5432/service");
    variables.put("DATASOURCE_USERNAME", "service_user");
    variables.put("DATASOURCE_PASSWORD", "synthetic-contract-test-password");
    variables.put("REDIS_URL", "redis://redis:6379");
    for (String service : CLIENTS) {
      variables.put(service.toUpperCase() + "_SERVICE_URL", "http://" + service + "-service:8080");
    }
    return variables;
  }

  private static Map<String, Object> compose() throws IOException {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    Yaml yaml = new Yaml(new SafeConstructor(options));
    return mapping(yaml.load(Files.readString(ROOT.resolve("infrastructure/docker-compose.yml"))));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapping(Object value) {
    return (Map<String, Object>) value;
  }
}
