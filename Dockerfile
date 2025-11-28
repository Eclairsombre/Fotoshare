# Build Stage
FROM maven AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean install -DskipTests

# Run Stage
FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar AppReseauPartagePhoto.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","AppReseauPartagePhoto.jar"]
