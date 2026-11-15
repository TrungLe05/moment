# Image backend (API). Build từ thư mục backend/: docker build -t moment-backend .
# Container worker có ffmpeg sẽ thêm khi chốt ADR 0003 (Q-009, T-132).

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

# --- Runtime: chỉ JRE, chạy bằng user không phải root ---
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
