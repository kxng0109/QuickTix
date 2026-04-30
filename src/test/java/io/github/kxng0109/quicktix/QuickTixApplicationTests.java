package io.github.kxng0109.quicktix;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class QuickTixApplicationTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgresqlContainer = new PostgreSQLContainer(
			DockerImageName.parse("postgres:alpine")
			               .asCompatibleSubstituteFor("postgres")
	);

	@Container
	static final RedisContainer redisContainer = new RedisContainer(
			DockerImageName.parse("redis:alpine")
			               .asCompatibleSubstituteFor("redis")
	);

	@Container
	@ServiceConnection
	static final RabbitMQContainer rabbitmqContainer = new RabbitMQContainer(
			DockerImageName.parse("heidiks/rabbitmq-delayed-message-exchange:3.13.0-management")
			               .asCompatibleSubstituteFor("rabbitmq")
	);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redisContainer::getHost);
		registry.add("spring.data.redis.port", redisContainer::getFirstMappedPort);
	}

	@Test
	void contextLoads() {
	}

}
