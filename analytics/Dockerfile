ARG IMAGE_JRE=eclipse-temurin:21-jre-alpine
FROM ${IMAGE_JRE}
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8092
ENTRYPOINT ["java", "-jar", "app.jar"]
