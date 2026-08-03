#!/usr/bin/env bash
# AI 코드 리뷰 루프 · 격리 세션 준비 (통합 설계 P2 · review-loop/UNIFIED_DESIGN.md §4)
#
# 드라이버(Claude Code)가 메인 워킹트리를 건드리지 않고 작업할 **worktree + 브랜치 + 변경 목록**만 만든다.
# 여기서 루프를 돌리지 않는다 — 판정·수정·검증·커밋은 전부 드라이버가 대화 안에서 한다.
#
# 구 review-autoloop.sh 에서 걷어낸 것과 이유:
#   - ./gradlew reviewAutoFix 호출 → 무인 fixer(Gemini)가 자기 판정을 자기가 고치는 자기승인 경로. 휴면.
#   - 자동 커밋 · --push       → 커밋·push는 사람 승인 게이트(팀 규칙). 드라이버가 git diff를 제시한다.
#   - 커밋 직전 ./gradlew test → scripts/review-verify.sh --with-test 로 승계(드라이버 7번 단계).
#   - GEMINI_API_KEY 요구      → 이 스크립트는 LLM을 부르지 않는다. 키는 판정 단계에서만 필요.
# 격리는 **선택**이다. 변경 파일이 많거나 수정을 버릴 가능성이 있을 때만 쓴다.
#
# 사용: bash scripts/review-session.sh [--base <ref>]
# 반환: 0 = 준비 완료(경로·브랜치·변경목록 출력). 2 = 인자 오류 또는 세션 worktree가 이미 있음.
set -u

ROOT="$(git rev-parse --show-toplevel)" || exit 1

# 비교 기준: --base 지정 → origin/develop → origin/main → 최초 커밋 순 폴백(신규 레포 방어)
CMP=""
while [ $# -gt 0 ]; do
  case "$1" in
    --base) CMP="${2:-}"; shift 2 ;;
    *) echo "[session] 알 수 없는 인자: $1  (사용: bash scripts/review-session.sh [--base <ref>])"; exit 2 ;;
  esac
done
if [ -z "$CMP" ]; then
  if git rev-parse --verify -q origin/develop >/dev/null 2>&1; then CMP=origin/develop
  elif git rev-parse --verify -q origin/main >/dev/null 2>&1; then CMP=origin/main
  else CMP="$(git rev-list --max-parents=0 HEAD | tail -1)"; fi
fi

BASE="$(git rev-parse --abbrev-ref HEAD)"
SHA="$(git rev-parse --short HEAD)"
BRANCH="review/${BASE}-${SHA}"
# 메인 트리 형제 위치 — 리포 안이 아니라 밖이라야 판정 대상(변경 .java)에 worktree 사본이 섞이지 않는다.
# 경로는 정규화해서 출력한다(사람이 그대로 복사해 쓴다). Git Bash에서는 `pwd -W`로 Windows 형식(C:/...)을
# 얻는다 — MSYS 형식(/c/...)은 PowerShell·cmd에 붙여넣으면 동작하지 않는다.
WT_PARENT="$(cd "${ROOT}/.." && { pwd -W 2>/dev/null || pwd; })"
WT="${WT_PARENT%/}/.review-wt"

if [ -e "$WT" ]; then
  echo "[session] 이미 세션 worktree가 있습니다: $WT"
  echo "[session] 정리:  git worktree remove --force $WT"
  exit 2
fi

git worktree add -b "$BRANCH" "$WT" HEAD >/dev/null || exit 1

# linked worktree에서 .git은 디렉터리가 아니라 '파일'이다(gitdir 포인터) → 그 아래에 쓰면 실패한다.
# git이 알려주는 실제 gitdir 경로에 쓴다.
CHANGED="$(cd "$WT" && git rev-parse --git-path reviewloop-changed.txt)"
: > "$CHANGED"
(cd "$WT" && git diff --name-only --diff-filter=ACMR "${CMP}...HEAD" | grep -E '\.java$' > "$CHANGED") || true
N=$(grep -c . "$CHANGED" 2>/dev/null || true)
N="${N:-0}"

echo "[session] ✅ 격리 세션 준비 완료 — 루프는 돌리지 않았다(드라이버가 운전)"
echo "  worktree  : $WT"
echo "  브랜치    : $BRANCH   (기준 $CMP)"
echo "  변경 .java: ${N}개 → $CHANGED"
echo
echo "다음(드라이버 루프 · review-loop/DRIVER.md):"
echo "  cd $WT"
echo "  ./gradlew reviewLoop --args=\"--files-from $CHANGED --findings-out build/review-minor.txt\""
echo "  bash scripts/review-fix-apply.sh build/review-minor.txt        # 방안 포함 요청서"
echo "  → Edit 수정 → bash scripts/review-verify.sh → 재판정 → --with-test → 승인·커밋"
echo
echo "세션 정리(브랜치는 로컬에 남는다):  git worktree remove --force $WT"
exit 0
