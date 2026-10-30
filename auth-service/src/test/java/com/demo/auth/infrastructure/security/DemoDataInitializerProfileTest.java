package com.demo.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class DemoDataInitializerProfileTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(DemoDataInitializer.class)
          .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));

  @Test
  void nonDevProfilesDoNotRequireDemoSecretsOrRegisterSeedRunner() {
    for (String profile : new String[] {"qa", "stage", "prod"}) {
      context
          .withInitializer(application -> application.getEnvironment().setActiveProfiles(profile))
          .run(
              application -> {
                assertThat(application).hasNotFailed();
                assertThat(application).doesNotHaveBean(ApplicationRunner.class);
                // Spring initializes the managed JdbcTemplate even when no seed runner exists.
                // Permit that lifecycle hook, but fail on every SQL or other application call.
                assertThat(mockingDetails(application.getBean(JdbcTemplate.class)).getInvocations())
                    .allMatch(
                        invocation ->
                            invocation.getMethod().getName().equals("afterPropertiesSet"));
              });
    }
  }

  @Test
  void devRegistersOneInitializerUsingConfiguredIdentityNames() {
    context
        .withInitializer(application -> application.getEnvironment().setActiveProfiles("dev"))
        .withPropertyValues(
            "demo.admin-username=local-admin", "demo.admin-password=local-admin-password",
            "demo.user-username=local-customer", "demo.user-password=local-user-password")
        .run(
            application -> {
              assertThat(application).hasNotFailed();
              assertThat(application).hasSingleBean(ApplicationRunner.class);
            });
  }

  @Test
  void invalidDemoCredentialsFailBeforeAnyDatabaseMutation() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    ApplicationRunner runner =
        new DemoDataInitializer()
            .initializeDemoData(jdbc, "admin", "weak", "customer", "long-user-password");

    assertThatThrownBy(() -> runner.run(null)).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(jdbc);
  }
}
