#!/usr/bin/env bash
# AI 코드 리뷰 루프 · reviewFix — Minor "수정 요청서" 생성 (판정=Gemini · 수정=Claude Code 드라이버)
#
# 흐름: ① Gemini 판정으로 Minor findings 추출 → ② 방안 포함 수정 요청서 생성
#       → ③ Claude Code(드라이버)가 Edit로 수정 → review-verify.sh 검증 → diff 승인 → 커밋·push
# 이 스크립트는 코드를 직접 고치지 않는다(중첩 claude 없음). Critical/미완성은 항상 사람.
# 절차 상세: review-loop/DRIVER.md
#
# 사용: bash scripts/review-fix.sh --files-from <git-dir>/reviewloop-changed.txt
#       bash scripts/review-fix.sh --path src/main/java/com/module06/backend/domain/meeting --domain meeting
# 필요: GEMINI_API_KEY(판정용)
set -u

# Gradle 런처 JVM을 UTF-8로 — 훅과 동일(Windows 콘솔 cp949에서 한글 깨짐 방지).
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dfile.encoding=UTF-8"

DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET_ARGS="$*"
FINDINGS="$(git rev-parse --git-dir)/review-minor-findings.txt"
REQUEST="$(git rev-parse --git-dir)/review-fix-request.md"

if [ -z "$TARGET_ARGS" ]; then
  echo "사용법: bash scripts/review-fix.sh (--path <dir> | --files-from <list>) [--domain X] [--max N]"
  exit 2
fi
if [ -z "${GEMINI_API_KEY:-}" ]; then
  echo "[reviewFix] GEMINI_API_KEY 필요(판정용)"; exit 1
fi

echo "[reviewFix] ① Gemini 판정 → Minor findings 추출"
if ! ./gradlew -q reviewLoop --args="$TARGET_ARGS --findings-out $FINDINGS" --no-daemon; then
  echo "[reviewFix] 판정 실패"; exit 1
fi

if [ ! -s "$FINDINGS" ]; then
  echo "[reviewFix] 수정할 Minor findings 없음 → 종료 (Critical/미완성이면 사람이 처리)"
  exit 0
fi

echo "[reviewFix] ② 방안 포함 수정 요청서 생성"
bash "$DIR/review-fix-apply.sh" "$FINDINGS" "$REQUEST"

echo "[reviewFix] ③ 다음 단계 — Claude Code(드라이버)에게 맡기세요:"
echo "      \"$REQUEST 처리해\"  → Edit 수정 → review-verify.sh → 재판정 → diff 승인·교훈 → 커밋 → 재push"
echo "      (예산: AutoFix ≤3 · Total ≤6 · Critical/미완성은 사람 · 상세: review-loop/DRIVER.md)"
