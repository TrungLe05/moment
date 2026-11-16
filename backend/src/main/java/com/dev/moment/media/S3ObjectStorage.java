package com.dev.moment.media;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * Cài đặt {@link ObjectStorage} bằng AWS SDK v2, chạy được với RustFS và Cloudflare R2 (cùng giao thức S3).
 * Có timeout cho mỗi lần gọi; SDK tự thử lại lỗi tạm thời với số lần giới hạn và chờ tăng dần (CLAUDE.md).
 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
class S3ObjectStorage implements ObjectStorage, AutoCloseable {

    private static final int NOT_FOUND = 404;

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Clock clock;

    S3ObjectStorage(StorageProperties properties, Clock clock) {
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        URI endpoint = URI.create(properties.endpoint());
        Region region = Region.of(properties.region());
        // Path-style (endpoint/bucket/key) chạy với mọi dịch vụ chuẩn S3, không cần DNS theo tên bucket.
        S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .apiCallAttemptTimeout(Duration.ofSeconds(4))
                        .build())
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        this.bucket = properties.bucket();
        this.clock = clock;
    }

    @Override
    public PresignedUpload presignUpload(String objectKey, String contentType, long sizeBytes, Duration ttl) {
        PresignedPutObjectRequest presigned = presigner.presignPutObject(request -> request
                .signatureDuration(ttl)
                .putObjectRequest(put -> put.bucket(bucket).key(objectKey).contentType(contentType)
                        .contentLength(sizeBytes)));
        return new PresignedUpload("PUT", presigned.url().toString(), Map.of("Content-Type", contentType),
                clock.instant().plus(ttl));
    }

    @Override
    public PresignedDownload presignDownload(String objectKey, Duration ttl) {
        PresignedGetObjectRequest presigned = presigner.presignGetObject(request -> request
                .signatureDuration(ttl)
                .getObjectRequest(get -> get.bucket(bucket).key(objectKey)));
        return new PresignedDownload(presigned.url().toString(), clock.instant().plus(ttl));
    }

    @Override
    public Optional<StoredObject> find(String objectKey) {
        try {
            HeadObjectResponse head = client.headObject(request -> request.bucket(bucket).key(objectKey));
            return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        } catch (S3Exception exception) {
            // HEAD không có body nên một số dịch vụ trả 404 chung chung thay vì NoSuchKey.
            if (exception.statusCode() == NOT_FOUND) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    @Override
    public void deleteAll(Collection<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        List<ObjectIdentifier> identifiers = objectKeys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();
        client.deleteObjects(request -> request.bucket(bucket).delete(delete -> delete.objects(identifiers)));
    }

    @Override
    public void close() {
        presigner.close();
        client.close();
    }
}
