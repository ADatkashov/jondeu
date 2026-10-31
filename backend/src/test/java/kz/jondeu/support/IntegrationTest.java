package kz.jondeu.support;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.lang.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Full application context against a real (embedded) PostgreSQL with Flyway migrations applied. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY, refresh = AutoConfigureEmbeddedDatabase.RefreshMode.NEVER)
@ActiveProfiles("test")
public @interface IntegrationTest {}
