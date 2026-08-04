#!/usr/bin/env bash
# =============================================================================
# 잇다 Spring 서버 배포 스크립트 (EC2에서 실행됨)
#
# 배치 위치: 이 파일을 각 Spring EC2의 /opt/itta/deploy.sh 에 미리 올려둔다.
#           (프로비저닝 시 1회 배치 — CD 워크플로가 SSM으로 이걸 호출만 한다)
# 호출 예시: sudo /opt/itta/deploy.sh <이미지태그(커밋SHA)>
#           태그는 필수다. CD는 latest 를 밀지 않으므로 생략 시 실패한다
#           (낡은 latest 를 조용히 배포하는 사고 방지).
#
# 전제:
#   - EC2에 docker 설치됨
#   - EC2 IAM Role에 ssm:GetParametersByPath 권한 있음 (아래 PARAM_PATH 대상)
#   - Docker Hub private 이미지면 EC2에서 docker login 되어 있어야 함
# =============================================================================
set -euo pipefail
umask 077

# ------- 환경에 맞게 수정 (인프라 확정되면 채우기) -------
DOCKER_USER="sungjinmo"                  # backend-ci.yml과 동일 (PR #8 기준)
IMAGE_NAME="z-spring"
REGION="ap-northeast-2"
PARAM_PATH="/z/prod/"               # SSM Parameter Store 경로 (SecureString 포함)
CONTAINER="z-spring"
APP_PORT="8080"
COMPOSE_FILE="/opt/itta/docker-compose.yml"
ENV_FILE="/opt/itta/.env.runtime"
# -------------------------------------------------------

# 이미지 태그(커밋 SHA)는 필수. 인자 없이 실행되면 어떤 버전을 띄우는지
# 불명확해지므로 즉시 실패시킨다(CD가 latest 를 더 이상 push 하지 않기 때문).
if [ "$#" -lt 1 ] || [ -z "${1:-}" ]; then
  echo "사용법: $(basename "$0") <이미지태그(커밋SHA)>" >&2
  exit 1
fi
IMAGE_TAG="$1"
# 커밋 SHA 형식(7~40자리 hex)만 허용한다. latest·develop·임의 문자열을 넘기면
# 커밋과 무관한 이미지가 배포될 수 있으므로 즉시 실패시킨다(운영=커밋 1:1 추적 보장).
if ! printf '%s' "${IMAGE_TAG}" | grep -Eq '^[0-9a-f]{7,40}$'; then
  echo "오류: 이미지 태그는 커밋 SHA(7~40자리 hex)여야 합니다: '${IMAGE_TAG}'" >&2
  exit 1
fi
export SPRING_IMAGE="${DOCKER_USER}/${IMAGE_NAME}:${IMAGE_TAG}"
export RUNTIME_ENV_FILE="${ENV_FILE}"

echo "=== [1/4] SSM Parameter Store에서 설정 로드: ${PARAM_PATH} ==="
# --output text 는 컬럼을 TAB으로 구분 → IFS=TAB 으로 값 안의 공백 보존
tmp_env="$(mktemp "${ENV_FILE}.tmp.XXXXXX")"
trap 'rm -f -- "${tmp_env}"' EXIT
aws ssm get-parameters-by-path \
  --path "${PARAM_PATH}" \
  --recursive \
  --with-decryption \
  --region "${REGION}" \
  --query "Parameters[].[Name,Value]" \
  --output text \
| while IFS=$'\t' read -r name value; do
    key="$(basename "${name}")"          # /z/prod/DB_PASSWORD -> DB_PASSWORD
    echo "${key}=${value}"
 done > "${tmp_env}"

if [[ ! -s "${tmp_env}" ]]; then
  echo "오류: Parameter Store에서 환경변수를 가져오지 못했습니다." >&2
  exit 1
fi

chmod 600 "${tmp_env}"
mv -f "${tmp_env}" "${ENV_FILE}"
trap - EXIT

echo "=== [2/4] Spring·Redis 이미지 pull: ${SPRING_IMAGE} ==="
docker compose -f "${COMPOSE_FILE}" pull spring redis

echo "=== [3/4] 컨테이너 교체 ==="
docker compose -f "${COMPOSE_FILE}" up -d --remove-orphans spring

echo "=== [4/4] 헬스체크 대기 ==="
for i in $(seq 1 30); do
  if curl -fsS "http://localhost:${APP_PORT}/actuator/health" >/dev/null 2>&1; then
    echo "배포 성공 — 헬스체크 통과"
    exit 0
  fi
  sleep 2
done

echo "헬스체크 실패 — 로그 확인 필요" >&2
docker logs --tail 50 "${CONTAINER}" >&2 || true
exit 1
