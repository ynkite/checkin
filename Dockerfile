# 체크인 배포 이미지
#
# 2단으로 나눈 이유 — 빌드에는 JDK 와 그래들 캐시가 필요하지만
# 돌릴 때는 JRE 만 있으면 된다. 최종 이미지가 작아야 EC2 1GB 에서 뜬다.
#
#   docker build -t checkin .
#   docker run --env-file .env -p 8080:8080 checkin

FROM gradle:8.14-jdk21 AS build
WORKDIR /src
COPY . .
# 실제 설정 파일은 .gitignore 대상이라 저장소에 없다.
# 예시 파일의 값이 전부 ${환경변수:} 형태라 그대로 복사해 쓴다.
RUN cp src/main/resources/application.properties.example src/main/resources/application.properties
RUN gradle bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar

# 업로드 파일은 컨테이너 밖 볼륨에 둔다. 재배포해도 남아야 한다.
ENV FILE_UPLOAD_PATH=/data/uploads/
VOLUME /data/uploads

# 컨테이너에 할당된 메모리의 70% 까지만 힙으로. 나머지는 JVM 자체와 여유분
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Seoul"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
