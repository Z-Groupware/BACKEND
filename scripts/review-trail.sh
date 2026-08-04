#!/usr/bin/env bash
# AI 코드 리뷰 루프 · 수정 이력(trail) 기록 — 학습 루프 자동화의 전제 (P4 · UNIFIED_DESIGN.md §8)
#
# "이 커밋이 어떤 finding을 고쳤는가"를 남긴다. 이 매핑이 없으면 나중에 커밋이 revert돼도
# 어느 규칙이 오탐이었는지 알 수 없다 — P4 자동 기록이 불가능한 진짜 이유가 이것이었다.
#
# 드라이버가 DRIVER.md 8번(커밋) 직후에 부른다:
#   bash scripts/review-trail.sh <findings-file>          # HEAD를 대상으로 기록
#   bash scripts/review-trail.sh <findings-file> <sha>    # 특정 커밋 지정
#
# findings-file 각 줄: 'path:line [RULE] 설명'  (reviewLoop --findings-out 형식)
# 출력: review-loop/logs/fix-trail.jsonl  (한 줄 = 커밋 하나 · 추적되는 누적 로그)
# 반환: 0 = 기록(또는 기록할 것 없음). 2 = 인자 오류.
set -u

FINDINGS="${1:-}"
if [ -z "$FINDINGS" ]; then
  echo "사용법: bash scripts/review-trail.sh <findings-file> [commit-sha]"; exit 2
fi
if [ ! -s "$FINDINGS" ]; then
  echo "[trail] findings 없음 → 기록할 것 없음"; exit 0
fi

ROOT="$(git rev-parse --show-toplevel)" || exit 2
SHA="$(git rev-parse --verify "${2:-HEAD}")" || exit 2
SUBJECT="$(git log -1 --format=%s "$SHA")"
WHEN="$(git log -1 --format=%cI "$SHA")"
TRAIL="${ROOT}/review-loop/logs/fix-trail.jsonl"
mkdir -p "$(dirname "$TRAIL")"

# 규칙 ID를 중복 없이 모은다 — 한 커밋이 같은 규칙을 여러 파일에서 고쳤을 수 있다.
RULES="$(grep -oE '\[[A-Z0-9_]+\]' "$FINDINGS" | tr -d '[]' | sort -u | paste -sd, -)"
if [ -z "$RULES" ]; then
  echo "[trail] findings에 규칙 ID가 없음 → 기록 생략"; exit 0
fi

# 이미 같은 커밋이 기록돼 있으면 덧붙이지 않는다(드라이버가 두 번 불러도 안전).
if [ -f "$TRAIL" ] && grep -q "\"commit\":\"$SHA\"" "$TRAIL"; then
  echo "[trail] 이미 기록된 커밋 → 생략: ${SHA:0:8}"; exit 0
fi

# 규칙 목록을 JSON 배열로. jq 없이 — 규칙 ID는 [A-Z0-9_]뿐이라 이스케이프가 필요 없다.
RULES_JSON="$(printf '%s' "$RULES" | awk -F, '{for(i=1;i<=NF;i++){printf "%s\"%s\"", (i>1?",":""), $i}}')"
COUNT="$(grep -c . "$FINDINGS")"

# subject는 사람이 쓴 문자열 → JSON 이스케이프 필요(따옴표·역슬래시·제어문자).
SUBJECT_JSON="$(printf '%s' "$SUBJECT" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | tr -d '\000-\037')"

printf '{"commit":"%s","when":"%s","subject":"%s","rules":[%s],"findings":%s}\n' \
  "$SHA" "$WHEN" "$SUBJECT_JSON" "$RULES_JSON" "$COUNT" >> "$TRAIL"

echo "[trail] 기록: ${SHA:0:8} · 규칙 ${RULES} · findings ${COUNT}건 → $TRAIL"
echo "[trail] 이 커밋이 나중에 revert되면: bash scripts/review-lesson-from-revert.sh"
exit 0
