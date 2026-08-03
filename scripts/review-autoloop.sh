#!/usr/bin/env bash
# 자율 수정 루프 래퍼 — 별도 worktree/브랜치에서 안전하게 실행 (결정 A/B/C)
#   A · pre-push 훅과 무관한 별도 태스크. 훅은 순수 게이트로 유지.
#   B · 수렴 수정분을 autoloop/<base>-<sha> 브랜치에 자동 커밋. push/PR은 --push일 때만(팀 규칙: 명시 승인).
#   C · Minor(NEEDS_REVISION)만 자율수정 — 러너가 Critical/미완성은 멈추고 사람 인계.
#
# 안전막(P1): 라운드 게이트는 in-JVM javac(단일 파일). 커밋 직전 ./gradlew test 1회로 전체 검증(교차 파괴 차단).
# 필요: GEMINI_API_KEY.  사용: bash scripts/review-autoloop.sh [--push]
set -euo pipefail
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dfile.encoding=UTF-8"

PUSH=0
for a in "$@"; do [ "$a" = "--push" ] && PUSH=1; done

if [ -z "${GEMINI_API_KEY:-}" ]; then
  echo "[autoloop] GEMINI_API_KEY 필요(판정·수정용)"; exit 1
fi

ROOT="$(git rev-parse --show-toplevel)"
BASE="$(git rev-parse --abbrev-ref HEAD)"
SHA="$(git rev-parse --short HEAD)"
BRANCH="autoloop/${BASE}-${SHA}"
WT="${ROOT}/../.autoloop-wt"

# 비교 기준: origin/develop → origin/main → 최초 커밋 순으로 폴백(신규 레포 방어)
if git rev-parse --verify -q origin/develop >/dev/null 2>&1; then CMP=origin/develop
elif git rev-parse --verify -q origin/main >/dev/null 2>&1; then CMP=origin/main
else CMP="$(git rev-list --max-parents=0 HEAD | tail -1)"; fi

git worktree add -b "$BRANCH" "$WT" HEAD >/dev/null
echo "[autoloop] worktree=$WT  branch=$BRANCH  기준=$CMP"

# 정상 종료 시 worktree 정리(브랜치는 로컬에 남음). 검토 필요 시(test 실패) 보존.
cleanup() { git worktree remove --force "$WT" >/dev/null 2>&1 || true; }
trap cleanup EXIT

cd "$WT"
# linked worktree에서 .git은 디렉터리가 아니라 '파일'이다(gitdir 포인터) → 그 아래에 쓰면 실패한다.
# git이 알려주는 실제 gitdir 경로에 쓴다.
CHANGED="$(git rev-parse --git-path autoloop-changed.txt)"
git diff --name-only --diff-filter=ACMR "${CMP}...HEAD" | grep -E '\.java$' > "$CHANGED" || true
N=$(wc -l < "$CHANGED" | tr -d ' ')
echo "[autoloop] 변경 .java ${N}개"
if [ "$N" -eq 0 ]; then echo "[autoloop] 대상 없음 → 종료"; exit 0; fi

./gradlew -q reviewAutoFix --args="--files-from $CHANGED --rounds-per-file 3 --global-budget 6" --no-daemon

if git diff --quiet; then
  echo "[autoloop] 수렴 수정 없음 → 커밋 없음"; exit 0
fi

echo "[autoloop] 전체 검증(./gradlew test) — 커밋 직전 최종 게이트"
if ! ./gradlew -q test --no-daemon; then
  echo "[autoloop] ❌ 전체 테스트 실패 → 커밋 취소. 수정본은 worktree에 보존: $WT"
  trap - EXIT   # 사람이 검토하도록 worktree 남김
  exit 1
fi

git add -A
git commit -m "fix(review): 자율 루프 Minor 수정 (base ${BASE})" >/dev/null
echo "[autoloop] ✅ 커밋 완료 · 브랜치 ${BRANCH}"

cd "$ROOT"
cleanup; trap - EXIT   # 커밋 성공 → worktree 제거(브랜치 유지)

if [ "$PUSH" -eq 1 ]; then
  echo "[autoloop] --push → git push -u origin ${BRANCH}"
  git push -u origin "$BRANCH"
else
  echo "[autoloop] 브랜치 로컬 생성 완료. push/PR은 승인 후:  git push -u origin ${BRANCH}"
fi
