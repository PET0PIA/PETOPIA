#!/usr/bin/env bash
#
# 인스턴스 A(애플리케이션, Amazon Linux 2023)에서 실행되는 배포 스크립트.
# GitHub Actions 가 SSM Send-Command 로 이 파일을 내려받아 실행한다.
#
#   ./deploy.sh <IMAGE_URI> <DEPLOY_SHA>
#
# 배포 로직을 워크플로에 인라인으로 박지 않고 저장소에 두는 이유:
#   - 리뷰와 버전 관리의 대상이 된다.
#   - 배포 SHA 로 고정해 내려받으므로, 실행되는 스크립트가 배포되는 이미지와
#     항상 같은 커밋이다.
#
# 주의: 이 스크립트의 표준 출력은 SSM 명령 결과로 워크플로 로그에 그대로 남는다.
#       따라서 set -x 를 쓰지 않고, .env 내용이나 헬스 응답 본문을 출력하지 않는다.
#       (헬스 응답에는 dbUser / dbVersion / connectionId 가 들어 있다.)
set -euo pipefail

IMAGE_URI="${1:?사용법: deploy.sh <IMAGE_URI> <DEPLOY_SHA>}"
DEPLOY_SHA="${2:?사용법: deploy.sh <IMAGE_URI> <DEPLOY_SHA>}"

REPO_SLUG="PET0PIA/PETOPIA"
APP_DIR="/opt/petopia"
COMPOSE_FILE="docker-compose.app.yaml"
SSM_PREFIX="/petopia/prod"
REGION="ap-northeast-2"
HEALTH_URL="http://127.0.0.1:8080/api/reservation/health"
HEALTH_TIMEOUT=180

log() { echo "[deploy] $*"; }

mkdir -p "$APP_DIR"
cd "$APP_DIR"

# ---------------------------------------------------------------------------
# 0. 사전 조건 확인
#
# Amazon Linux 2023 의 `dnf install docker` 는 엔진만 설치하고 compose 플러그인은
# 넣어주지 않는다. 없는 상태로 진행하면 아래에서 command not found 로 죽는데,
# SSM 로그만 보고 원인을 알아채기 어렵다. 여기서 먼저 끊고 해결 방법을 알린다.
# ---------------------------------------------------------------------------
if ! docker compose version > /dev/null 2>&1; then
  echo "[deploy] 오류: 이 인스턴스에 docker compose 플러그인이 없다." >&2
  echo "[deploy] 아래를 실행한 뒤 다시 배포한다." >&2
  echo "[deploy]   sudo mkdir -p /usr/local/lib/docker/cli-plugins" >&2
  echo "[deploy]   sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose" >&2
  echo "[deploy]   sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# 1. compose 파일을 배포 SHA 로 고정해 내려받는다.
#
# 저장소가 public 이라 자격 증명이 필요 없다. git clone 대신 raw URL 을 쓰는 것은
# git 설치가 불필요하고 전송량이 5MB+ 에서 2KB 로 줄기 때문이다.
# SHA 로 고정하므로 compose 파일과 배포 이미지가 항상 같은 커밋이다.
#
# 저장소를 private 으로 전환하면 이 경로가 막힌다. 그때는 S3 경유로 바꿔야 한다.
# ---------------------------------------------------------------------------
log "compose 파일 수신 (SHA ${DEPLOY_SHA})"
curl -fsSL --retry 3 --retry-delay 5 \
  "https://raw.githubusercontent.com/${REPO_SLUG}/${DEPLOY_SHA}/${COMPOSE_FILE}" \
  -o "${COMPOSE_FILE}.tmp"
mv "${COMPOSE_FILE}.tmp" "${COMPOSE_FILE}"

# ---------------------------------------------------------------------------
# 2. SSM Parameter Store 에서 설정을 받아 .env 를 만든다.
#
# get-parameters-by-path 를 쓰므로 파라미터를 추가하면 자동으로 흘러 들어온다.
# 워크플로나 이 스크립트를 고칠 필요가 없다. DB_HOST 도 여기 포함된다.
#
# jq 에 의존하지 않으려고 --query + --output text 로 탭 구분 출력을 받는다.
# (AL2023 에 jq 는 기본 설치되지 않는다.)
# ---------------------------------------------------------------------------
log "SSM 파라미터 조회 (${SSM_PREFIX})"
umask 077
: > .env.tmp
# 아래 검증 중 어디서 죽어도 복호화된 시크릿이 담긴 임시 파일을 남기지 않는다.
# 성공 경로에서는 mv 로 사라지므로 trap 이 지울 것이 없다.
trap 'rm -f "${APP_DIR}/.env.tmp"' EXIT
echo "APP_IMAGE=${IMAGE_URI}" >> .env.tmp

# 먼저 파라미터 개수를 받아 둔다. 아래 루프가 읽은 줄 수와 비교해
# 값에 줄바꿈이 섞였는지 검증하기 위함이다. (자세한 이유는 루프 아래 참고)
#
# get-parameters-by-path 는 페이지네이션 API 이고 MaxResults 기본값이 10 이다.
# 파라미터가 11개가 되는 순간 CLI 가 페이지를 나눠 호출하는데, --query 는
# 페이지마다 적용되므로 length(Parameters) 가 "10" 이 아니라 "10\n3" 처럼 나온다.
# 그 값을 [ -ne ] 에 넣으면 "integer expression expected" 로 비교가 상태 2 로 죽고,
# if 조건 안의 실패는 set -e 가 잡지 않아 본문(배포 중단)을 건너뛰고 그냥 진행한다.
# 즉 검증이 fail-open 이 된다. 현재 파라미터가 정확히 10개라 한 개만 더 늘면 걸린다.
#
# 그래서 줄 단위로 읽어 합산하고, 숫자가 아닌 줄이 하나라도 있으면 중단한다.
# 페이지가 하나뿐이어도 합계는 그 값 그대로라 두 경우 모두에서 맞다.
expected_count=0
# `|| [ -n ... ]` 는 마지막 줄에 개행이 없어도 버리지 않기 위한 관용구다.
while read -r page_count || [ -n "$page_count" ]; do
  case "$page_count" in
    ''|*[!0-9]*)
      echo "[deploy] 오류: 파라미터 개수 조회 결과가 정수가 아니다: '${page_count}'" >&2
      exit 1
      ;;
  esac
  expected_count=$((expected_count + page_count))
done < <(
  aws ssm get-parameters-by-path \
    --path "$SSM_PREFIX" \
    --region "$REGION" \
    --query 'length(Parameters)' \
    --output text
)

# 프로세스 치환은 종료 코드를 밖으로 전달하지 않는다. aws 호출 자체가 실패하면
# 루프가 한 번도 돌지 않아 0 이 남으므로, 여기서 끊어야 원인이 분명하다.
if [ "$expected_count" -eq 0 ]; then
  echo "[deploy] 오류: ${SSM_PREFIX} 의 파라미터 개수를 받지 못했다." >&2
  echo "[deploy] 인스턴스 역할의 ssm:GetParametersByPath 권한과 파라미터 경로를 확인한다." >&2
  exit 1
fi

param_count=0
line_count=0
while IFS=$'\t' read -r name value; do
  # 빈 줄까지 세는 것이 핵심이다. 아래 검증 주석 참고.
  line_count=$((line_count + 1))
  [ -z "${name:-}" ] && continue
  echo "${name##*/}=${value}" >> .env.tmp
  param_count=$((param_count + 1))
done < <(
  aws ssm get-parameters-by-path \
    --path "$SSM_PREFIX" \
    --with-decryption \
    --region "$REGION" \
    --query 'Parameters[].[Name,Value]' \
    --output text
)

if [ "$param_count" -eq 0 ]; then
  echo "[deploy] 오류: ${SSM_PREFIX} 에서 파라미터를 하나도 받지 못했다." >&2
  echo "[deploy] 인스턴스 역할의 ssm:GetParametersByPath 권한과 파라미터 경로를 확인한다." >&2
  exit 1
fi

# --output text 는 값에 줄바꿈이 있으면 그대로 여러 줄로 출력한다. 그러면 위 루프가
# 두 번째 줄부터를 새 파라미터로 오인해 .env 에 쓰레기 줄을 쓰고 원래 값은 잘린다.
# 조용히 깨지기 때문에 비밀번호가 잘린 채로 배포되어도 알아채기 어렵다.
#
# JSON 파싱으로 바꾸지 않는 이유: .env 형식(docker compose 의 env_file)은 애초에
# KEY=VALUE 한 줄 단위라 여러 줄 값을 담을 수 없다. 정확히 파싱해도 결과물이 깨진다.
# 즉 여러 줄 값은 지원 불가능한 입력이므로, 올바른 동작은 명확히 거부하는 것이다.
#
# 읽은 줄 수가 실제 파라미터 개수와 다르면 어딘가에 줄바꿈이 섞인 것이다.
#
# param_count 가 아니라 line_count 로 비교한다. 값이 후행 개행으로 끝나는 경우
# (VALUE="secret\n") 출력은 "NAME<TAB>secret\n\n" 이 되어 마지막에 빈 줄이 하나
# 더 붙는데, 빈 줄은 위에서 continue 로 건너뛰므로 param_count 는 늘지 않는다.
# 그러면 개수가 맞아떨어져 검증을 통과하고, 개행이 잘린 값이 조용히 배포된다.
# 빈 줄까지 세면 이 경우도 1건 기대에 2줄 수신으로 잡힌다.
if [ "$line_count" -ne "$expected_count" ]; then
  echo "[deploy] 오류: 파라미터 개수가 맞지 않는다 (기대 ${expected_count}건, 읽음 ${line_count}줄)." >&2
  echo "[deploy] 값에 줄바꿈이 들어간 파라미터가 있을 가능성이 높다." >&2
  echo "[deploy] .env 형식은 여러 줄 값을 담을 수 없으므로 해당 값을 한 줄로 바꿔야 한다." >&2
  exit 1
fi

log "파라미터 ${param_count}건 수신"

# 앱 기동에 반드시 필요한 값이 실제로 들어왔는지 확인한다.
# 없으면 컨테이너가 뜨다 죽으므로 여기서 먼저 끊는 편이 진단이 빠르다.
for required in DB_HOST DB_PASSWORD REDIS_PASSWORD ENTRY_QR_SECRET; do
  if ! grep -q "^${required}=." .env.tmp; then
    echo "[deploy] 오류: 필수 값 ${required} 가 비어 있거나 없다." >&2
    exit 1
  fi
done

chmod 600 .env.tmp
mv .env.tmp .env

# ---------------------------------------------------------------------------
# 3. ECR 로그인 후 이미지를 받아 교체한다.
# ---------------------------------------------------------------------------
ECR_REGISTRY="${IMAGE_URI%%/*}"
log "ECR 로그인 (${ECR_REGISTRY})"
aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

log "이미지 수신 ${IMAGE_URI}"
docker compose -f "$COMPOSE_FILE" pull

log "컨테이너 교체"
docker compose -f "$COMPOSE_FILE" up -d

# ---------------------------------------------------------------------------
# 4. 실제로 떴는지 확인한다.
#
# 이 확인이 없으면 "배포 성공" 초록불 아래 죽은 앱이 남는다.
# SSM 은 스크립트 종료 코드로 성패를 판정하므로 반드시 exit 1 로 끝내야 한다.
#
# 첫 기동이나 DB 재시작 직후에는 Flyway 가 MySQL 을 기다리며 컨테이너가
# 몇 차례 재시작할 수 있다(connection-timeout 3초). 그래서 넉넉히 기다린다.
# ---------------------------------------------------------------------------
log "헬스체크 대기 (최대 ${HEALTH_TIMEOUT}초)"
deadline=$(( SECONDS + HEALTH_TIMEOUT ))
until curl -fsS -o /dev/null --max-time 5 "$HEALTH_URL"; do
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "[deploy] 오류: ${HEALTH_TIMEOUT}초 안에 헬스체크가 200 을 반환하지 않았다." >&2
    echo "----- 컨테이너 상태 -----" >&2
    docker compose -f "$COMPOSE_FILE" ps >&2 || true
    echo "----- 앱 로그 (마지막 200줄) -----" >&2
    docker compose -f "$COMPOSE_FILE" logs --tail=200 app >&2 || true
    exit 1
  fi
  sleep 5
done

# 헬스 응답 본문은 출력하지 않는다. dbUser / dbVersion / connectionId 가 들어 있다.
log "헬스체크 통과 (${SECONDS}초)"

# 이전 배포에서 남은 이미지를 정리한다. 8GB 볼륨이라 방치하면 쌓인다.
# 현재 실행 중인 이미지는 dangling 이 아니므로 지워지지 않는다.
docker image prune -f > /dev/null 2>&1 || true

log "배포 완료 ${IMAGE_URI}"
