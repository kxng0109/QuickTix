package io.github.kxng0109.quicktix.integration;

import com.redis.testcontainers.RedisContainer;
import io.github.kxng0109.quicktix.dto.request.LoginRequest;
import io.github.kxng0109.quicktix.entity.User;
import io.github.kxng0109.quicktix.enums.Role;
import io.github.kxng0109.quicktix.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer postgresqlContainer = new PostgreSQLContainer(
			DockerImageName.parse("postgres:alpine")
			               .asCompatibleSubstituteFor("postgres")
	);

	static final RedisContainer redisContainer = new RedisContainer(
			DockerImageName.parse("redis:alpine")
			               .asCompatibleSubstituteFor("redis")
	);

	@ServiceConnection
	static final RabbitMQContainer rabbitmqContainer = new RabbitMQContainer(
			DockerImageName.parse("heidiks/rabbitmq-delayed-message-exchange:3.13.0-management")
					.asCompatibleSubstituteFor("rabbitmq")
	);

	static {
		postgresqlContainer.start();
		redisContainer.start();
	}

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redisContainer::getHost);
		registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
	}

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected UserRepository userRepository;

	@Autowired
	protected PasswordEncoder passwordEncoder;

	@Autowired
	private StringRedisTemplate  stringRedisTemplate;

	@BeforeEach
	void flushRedisState() {
		// This instantly wipes all rate-limit buckets and blacklisted JWTs
		// before every single test, giving you a completely clean slate.
		assert stringRedisTemplate.getConnectionFactory() != null;
		stringRedisTemplate.getConnectionFactory()
		                   .getConnection()
		                   .serverCommands()
		                   .flushDb();
	}

	protected User createUser(String email){
		User user = User.builder()
		                .firstName("Jane")
		                .lastName("Smith")
		                .email(email)
		                .passwordHash(passwordEncoder.encode("password123"))
		                .build();

		return userRepository.save(user);
	}

	protected String createUserAndGetToken(String email) throws Exception {
		createUser(email);
		LoginRequest userLoginRequest = LoginRequest.builder()
		                                            .email(email)
		                                            .password("password123")
		                                            .build();

		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/login")
				                                           .contentType(MediaType.APPLICATION_JSON)
				                                           .content(objectMapper.writeValueAsString(userLoginRequest)))
		                                  .andExpect(status().isOk())
		                                  .andReturn();

		return objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("token").asText();
	}

	protected String getAdminToken() throws Exception {
		userRepository.save(User.builder()
		                        .firstName("Admin")
		                        .lastName("User")
		                        .email("admin@test.com")
		                        .passwordHash(passwordEncoder.encode("password123"))
		                        .role(Role.ADMIN)
		                        .build());

		LoginRequest adminLoginRequest = LoginRequest.builder()
		                                             .email("admin@test.com")
		                                             .password("password123")
		                                             .build();

		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/login")
				                                           .contentType(MediaType.APPLICATION_JSON)
				                                           .content(objectMapper.writeValueAsString(adminLoginRequest)))
		                                  .andExpect(status().isOk())
		                                  .andReturn();

		return objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("token").asText();
	}
}
