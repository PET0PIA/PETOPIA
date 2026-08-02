# PETOPIA 애플리케이션 이미지 (linux/amd64, t3.micro 배포용)
#
# jar는 이 Dockerfile이 빌드하지 않는다. CI가 ./gradlew build 로 만든,
# 테스트를 통과한 바로 그 jar를 COPY 한다. 여기서 Gradle을 다시 돌리면
# CI의 의존성 캐시를 못 쓰고, 무엇보다 "테스트한 산출물"과 "배포하는 산출물"이
# 달라질 여지가 생긴다.
#
# 빌드 방법:
#   ./gradlew build
#   docker build -t petopia:<sha> .

# 베이스는 glibc(jammy)를 쓴다. alpine(musl)이 ~100MB 작지만 EBS 8GB와
# ECR $0.10/GB-월 기준에서 차이가 무의미하고, 1GiB 인스턴스에서 musl 관련
# 문제를 디버깅하는 비용이 더 크다.
ARG BASE_IMAGE=eclipse-temurin:21-jre-jammy

# ---------------------------------------------------------------------------
# 1단계: 레이어 추출
#
# fat jar(37.8MB)를 통째로 한 레이어에 넣으면 커밋 한 줄만 바뀌어도 매번
# 37.8MB를 ECR에 올리고 인스턴스가 그만큼 내려받는다.
# 실측: dependencies/lib 37.5MB(73개) / application/app.jar 82.6KB.
# 레이어를 나누면 배포마다 바뀌는 건 82KB뿐이다.
#
# Boot 4.x 문법은 -Djarmode=tools ... extract 다. 구버전 layertools 가 아니다.
# --launcher 를 쓰지 않으면 application/app.jar 가 Main-Class 를 직접 갖고
# Class-Path 로 lib/ 를 참조하는 thin jar 가 된다. (실행: java -jar app.jar)
# ---------------------------------------------------------------------------
FROM ${BASE_IMAGE} AS extractor
WORKDIR /build

# build/libs 에는 bootJar 와 -plain.jar 두 개가 생긴다.
# -plain.jar 는 .dockerignore 에서 제외했으므로 여기 와일드카드는 하나만 잡는다.
# 이름을 app.jar 로 고정해야 추출 결과도 application/app.jar 로 결정된다
# (추출된 앱 jar 이름은 소스 jar 이름을 그대로 따른다).
COPY build/libs/*.jar app.jar

RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------------------------------------------------------------------------
# 2단계: 런타임
# ---------------------------------------------------------------------------
FROM ${BASE_IMAGE}

# 비루트로 실행한다. temurin 이미지에는 비루트 계정이 없어 직접 만든다.
RUN groupadd --system --gid 1001 petopia \
 && useradd --system --uid 1001 --gid petopia --no-create-home petopia

WORKDIR /app

# 변경 빈도가 낮은 레이어부터 복사해야 Docker 캐시가 산다.
# dependencies(37.5MB)는 의존성이 바뀔 때만, application(82KB)은 매 빌드 변경.
# snapshot-dependencies 는 현재 비어 있지만 디렉터리는 생성되므로 COPY 가
# 실패하지 않는다. SNAPSHOT 의존성이 추가되면 이 줄이 그때부터 일한다.
COPY --from=extractor --chown=petopia:petopia /build/extracted/dependencies/ ./
COPY --from=extractor --chown=petopia:petopia /build/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=petopia:petopia /build/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=petopia:petopia /build/extracted/application/ ./

USER petopia

# 시간대. 없으면 컨테이너가 UTC로 떠서 ErrorResponse / HealthCheckResponse 의
# LocalDateTime.now() 4곳이 DB와 9시간 어긋난다.
# (업무 시간은 ReservationTimeProvider 가 ZoneId 를 명시해 영향받지 않는다.)
# TZ 는 OS 레벨, -Duser.timezone 은 JVM 레벨. 둘 다 박아 애매함을 없앤다.
ENV TZ=Asia/Seoul

# JVM 옵션은 JAVA_TOOL_OPTIONS 로 준다. ENTRYPOINT 를 exec 형식으로 유지해
# java 가 PID 1 이 되어 SIGTERM 을 직접 받게 하면서도, compose 에서
# 환경변수만 덮어써 재빌드 없이 튜닝할 수 있다.
#
# MaxRAMPercentage: 컨테이너 기본값은 25%라 mem_limit 640m 기준 힙이 ~160MB로
# 과소하다. 70%면 ~448MB. 나머지는 메타스페이스 / 스레드 스택 / 다이렉트 버퍼 몫.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -Duser.timezone=Asia/Seoul"

# 프로필을 지정하지 않으면 application.yaml 의 spring.profiles.default 에 따라
# local 로 뜬다. 운영에서는 반드시 release 여야 하므로 이미지 기본값으로 박는다.
ENV SPRING_PROFILES_ACTIVE=release

EXPOSE 8080

# HEALTHCHECK 는 두지 않는다. 이미지에 curl/wget 이 없고, 배포 검증은
# SSM 스크립트가 호스트에서 /api/reservation/health 를 폴링해 수행한다.

ENTRYPOINT ["java", "-jar", "app.jar"]
