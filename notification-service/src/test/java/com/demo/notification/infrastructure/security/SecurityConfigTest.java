package com.demo.notification.infrastructure.security;

import com.demo.notification.api.controller.NotificationController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigTest.TestConfiguration.class)
class SecurityConfigTest {
    private static final Path PUBLIC_KEY_PATH = createPublicKeyFile();

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key-path", () -> PUBLIC_KEY_PATH.toString());
        registry.add("security.jwt.issuer", () -> "test-issuer");
        registry.add("security.jwt.audience", () -> "test-audience");
    }

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void requiresCsrfTokenForUnsafeRequests() throws Exception {
        mockMvc.perform(post("/api/notifications"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/notifications").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    private static Path createPublicKeyFile() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'})
                    .encodeToString(generator.generateKeyPair().getPublic().getEncoded());
            Path path = Files.createTempFile("notification-test-public-key", ".pem");
            path.toFile().deleteOnExit();
            Files.writeString(path, "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----");
            return path;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, NotificationController.class})
    static class TestConfiguration {
        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }
    }
}
