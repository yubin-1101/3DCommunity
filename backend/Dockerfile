FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY lombok.config .
COPY src src
RUN chmod +x gradlew
# Gradle 메모리 설정 및 병렬 빌드 비활성화로 안정성 향상
RUN ./gradlew clean build -x test --no-daemon --no-parallel -Dorg.gradle.jvmargs="-Xmx512m -XX:MaxMetaspaceSize=256m"

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dserver.port=${PORT:-8080}", "-Dserver.address=0.0.0.0", "-jar", "app.jar"]
