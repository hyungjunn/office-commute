# 백엔드 런타임 이미지 (docs/DEPLOYMENT.md §3)
# jar 는 이미지 밖에서 빌드한다: ./gradlew bootJar → build/libs/*.jar 하나가 생긴다.
# (CI/CD 도, 로컬 풀스택 검증(docker-compose.yml --profile full)도 같은 방식 — 운영·로컬 이미지 파이프라인 동일)
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
