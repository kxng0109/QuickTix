package io.github.kxng0109.quicktix.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Base class for all integration tests.
 * <p>
 * Annotations explained:
 *
 * @SpringBootTest — Loads the FULL Spring application context, including all beans,
 * configurations, and auto-configurations. This is different from
 * {@code @WebMvcTest} which only loads the web layer.
 *
 * @AutoConfigureMockMvc — Configures MockMvc to work with the full context.
 * We can still use MockMvc to make HTTP requests, but now
 * those requests go through the real service and repository layers.
 *
 * @ActiveProfiles("test") — Activates the "test" profile, which will load
 * application-test.properties. This lets us configure
 * a separate test database.
 *
 * @DirtiesContext — Resets the Spring context after each test class.
 * This ensures test isolation but makes tests slower.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)

public abstract class BaseIntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ObjectMapper objectMapper;
}
