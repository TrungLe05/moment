package com.dev.moment;

import java.net.URI;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Hạ tầng thật cho integration test (CLAUDE.md, kiểm thử): PostgreSQL và object storage chuẩn S3 RustFS (D-010).
 * Khóa RustFS sinh ngẫu nhiên mỗi lần chạy, không nằm cứng trong code.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final String BUCKET = "moment-test";
	private static final String ACCESS_KEY = "test-" + UUID.randomUUID().toString().substring(0, 8);
	private static final String SECRET_KEY = UUID.randomUUID().toString();

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

	@Bean
	GenericContainer<?> rustfsContainer() {
		GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:1.0.6"))
				.withEnv("RUSTFS_VOLUMES", "/data")
				.withEnv("RUSTFS_ADDRESS", "0.0.0.0:9000")
				.withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
				.withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
				.withExposedPorts(9000)
				.waitingFor(Wait.forHttp("/health").forPort(9000));
		container.start();
		createBucket(endpoint(container));
		return container;
	}

	@Bean
	DynamicPropertyRegistrar storageProperties(GenericContainer<?> rustfsContainer) {
		return registry -> {
			registry.add("moment.storage.endpoint", () -> endpoint(rustfsContainer));
			registry.add("moment.storage.region", () -> "us-east-1");
			registry.add("moment.storage.bucket", () -> BUCKET);
			registry.add("moment.storage.access-key", () -> ACCESS_KEY);
			registry.add("moment.storage.secret-key", () -> SECRET_KEY);
		};
	}

	private static String endpoint(GenericContainer<?> container) {
		return "http://" + container.getHost() + ":" + container.getMappedPort(9000);
	}

	// RustFS báo /health sẵn sàng hơi sớm hơn lúc nhận lệnh S3 (thấy 503), nên thử lại có giới hạn.
	private static void createBucket(String endpoint) {
		try (S3Client client = S3Client.builder()
				.endpointOverride(URI.create(endpoint))
				.region(Region.US_EAST_1)
				.forcePathStyle(true)
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
				.build()) {
			long backoffMillis = 250;
			for (int attempt = 1; ; attempt++) {
				try {
					client.createBucket(request -> request.bucket(BUCKET));
					return;
				} catch (S3Exception exception) {
					if (exception.statusCode() != 503 || attempt >= 8) {
						throw exception;
					}
					sleep(backoffMillis);
					backoffMillis = Math.min(backoffMillis * 2, 2000);
				}
			}
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(interrupted);
		}
	}
}
