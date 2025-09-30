# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mkdir -p /root/.m2 && \
    cat > /root/.m2/settings.xml <<'EOF'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <mirrors>
    <mirror>
      <id>mirror-default</id>
      <mirrorOf>*</mirrorOf>
      <url>__MIRROR_URL__</url>
    </mirror>
  </mirrors>
</settings>
EOF
RUN --mount=type=cache,target=/root/.m2 \
    sed -i "s|__MIRROR_URL__|${MAVEN_MIRROR_URL}|g" /root/.m2/settings.xml && \
    mvn -s /root/.m2/settings.xml -q -DskipTests package

# ---------- runtime stage ----------
FROM eclipse-temurin:17-jre-focal AS runtime
ENV TZ=UTC \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -XX:MaxRAMPercentage=75.0"
WORKDIR /app
COPY --from=build /workspace/target/dts-admin-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
