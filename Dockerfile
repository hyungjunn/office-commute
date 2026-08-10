# 백엔드 런타임 이미지 (docs/DEPLOYMENT.md §3)
# 빌드는 CI 에서: ./gradlew bootJar → build/libs/*.jar 하나가 생긴다.
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
