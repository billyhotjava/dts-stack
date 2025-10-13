# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests package

# ---------- runtime stage ----------
FROM eclipse-temurin:21-jre AS runtime
ENV TZ=UTC \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -XX:MaxRAMPercentage=75.0"
WORKDIR /app
COPY --from=build /workspace/target/dts-common-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

