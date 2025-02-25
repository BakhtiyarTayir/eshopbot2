FROM eclipse-temurin:21-jdk

WORKDIR /app

# Копируем только pom.xml сначала для кэширования зависимостей
COPY pom.xml .
RUN mkdir -p src/main/java src/main/resources

# Запускаем Maven в автономном режиме для загрузки зависимостей
RUN apt-get update && apt-get install -y maven && \
    mvn dependency:go-offline && \
    apt-get clean

# Точка входа для разработки с поддержкой горячей перезагрузки
ENTRYPOINT ["mvn", "spring-boot:run", "-Dspring-boot.run.jvmArguments='-Dspring.devtools.restart.enabled=true'"] 