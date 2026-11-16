# Image backend. Build từ thư mục backend/:
#   API:    docker build -t moment-backend .
#   Worker: docker build --target worker -t moment-worker .   (có ffmpeg, chạy profile worker, ADR 0003)

# --- Build: biên dịch và đóng gói jar ---
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Gọi qua sh vì commit từ Windows thường không giữ quyền thực thi của mvnw.
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q dependency:go-offline
COPY src/ src/
# Test đã chạy ở job CI riêng (cần Docker cho Testcontainers), nên bỏ qua khi build image.
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q -DskipTests package \
    && mkdir -p target/extracted \
    && java -Djarmode=tools -jar target/moment-*.jar extract --layers --launcher --destination target/extracted

# --- Worker: thêm ffmpeg, chạy profile worker (ARCHITECTURE.md mục 2) ---
FROM eclipse-temurin:25-jre-alpine AS worker
RUN apk add --no-cache ffmpeg \
    && addgroup -S moment && adduser -S -G moment moment
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER moment
ENV SPRING_PROFILES_ACTIVE=worker
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8090/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]

# --- API (mặc định, stage cuối): chỉ JRE, chạy bằng user không phải root, không có ffmpeg ---
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S moment && adduser -S -G moment moment
WORKDIR /app
# Chép theo lớp: thư viện ít đổi nằm dưới, code ứng dụng nằm trên cùng để tận dụng cache.
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER moment
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
