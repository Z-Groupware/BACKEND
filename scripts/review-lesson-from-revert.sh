#!/usr/bin/env bash
# AI 코드 리뷰 루프 · revert된 수정 → FALSE_POSITIVE 자동 기록 (P4 · UNIFIED_DESIGN.md §8)
#
# ── 설계 문안에서 무엇을 뺐고 왜인가 ────────────────────────────────────────
# 초안 P4는 "커밋이 revert되거나 **CI 실패 시** FALSE_POSITIVE 자동 기록"이었다.
# CI 실패는 근거가 안 된다 — 그건 보통 "우리 수정이 틀렸다"는 신호이지 "Judge의 지적이 오탐이었다"는
# 신호가 아니다. 그걸로 FALSE_POSITIVE를 적재하면 잘못된 교훈이 판정 프롬프트에 주입돼
# Judge가 진짜 위반을 놓치기 시작한다(학습 루프가 스스로를 망가뜨린다).
# 그래서 **revert만** 쓴다. revert는 사람이 "이 수정은 하지 말았어야 했다"고 명시적으로 뒤집은 것이라
# 오탐의 근거로 충분하다.
#
# 사용: bash scripts/review-lesson-from-revert.sh [--dry-run] [--since <ref>]
#   --dry-run  기록하지 않고 무엇을 기록할지만 출력
#   --since    검사 시작 지점(기본: 최근 200커밋)
# 필요: review-loop/logs/fix-trail.jsonl (scripts/review-trail.sh가 커밋 시 남긴다)
# 반환: 0 = 완료(기록 0건이어도 0). 2 = 인자·전제 오류.
set -u

DRY=0
SINCE=""
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY=1; shift ;;
    --since) SINCE="${2:-}"; shift 2 ;;
    *) echo "[revert-lesson] 알 수 없는 인자: $1"; exit 2 ;;
  esac
done

ROOT="$(git rev-parse --show-toplevel)" || exit 2
TRAIL="${ROOT}/review-loop/logs/fix-trail.jsonl"
LESSONS="${ROOT}/review-loop/knowledge/lessons.jsonl"

# 멱등성 근거는 lessons.jsonl 자체다 — 별도 '처리했음' 파일을 두지 않는다.
# 그 파일은 로컬인데 lessons.jsonl은 팀 공유라, 클론마다 같은 revert를 다시 기록해
# 오탐 집계(reviewAccuracy의 분자)를 부풀린다. note에 아래 토큰을 심어 두면
# "이미 기록됐는지"를 공유 파일만 보고 판정할 수 있다.
MARK="auto:revert"

if [ ! -s "$TRAIL" ]; then
  echo "[revert-lesson] 수정 이력이 없다: $TRAIL"
  echo "[revert-lesson] 드라이버가 커밋 직후 scripts/review-trail.sh를 부르면 쌓인다 → 지금은 할 일 없음"
  exit 0
fi

RANGE="${SINCE:+$SINCE..HEAD}"
RANGE="${RANGE:--n 200}"

recorded=0
skipped=0

# revert 커밋 찾기 — git revert가 본문에 남기는 'This reverts commit <sha>.'를 근거로 쓴다.
# (--grep은 제목/본문 모두 검색. 손으로 되돌린 커밋은 이 표식이 없어 잡히지 않는다 — 의도된 한계.)
# shellcheck disable=SC2086
for revert_sha in $(git log --format=%H --grep='^This reverts commit' $RANGE 2>/dev/null); do
  reverted="$(git log -1 --format=%B "$revert_sha" \
    | sed -n 's/^This reverts commit \([0-9a-f]\{7,40\}\).*$/\1/p' | head -1)"
  [ -z "$reverted" ] && continue

  full="$(git rev-parse --verify "$reverted" 2>/dev/null)" || continue

  # 그 커밋이 리뷰 루프 수정이었나 — trail에 있어야만 규칙을 특정할 수 있다.
  entry="$(grep -F "\"commit\":\"$full\"" "$TRAIL" | tail -1)"
  [ -z "$entry" ] && continue

  rules="$(printf '%s' "$entry" | sed -n 's/.*"rules":\[\([^]]*\)\].*/\1/p' | tr -d '"' | tr ',' ' ')"
  [ -z "$rules" ] && continue

  failed=0
  for rule in $rules; do
    note="[${MARK} ${revert_sha:0:8}→${full:0:8}] revert된 수정 — 사람이 이 지적의 수정을 되돌렸다"

    # 이미 이 (revert, rule) 조합이 lessons에 있으면 건너뛴다 — 공유 파일이 유일한 진실.
    if [ -f "$LESSONS" ] \
       && grep -F "${MARK} ${revert_sha:0:8}" "$LESSONS" | grep -qF "\"ruleId\":\"$rule\""; then
      skipped=$((skipped+1)); continue
    fi

    if [ "$DRY" -eq 1 ]; then
      echo "[revert-lesson][dry] $rule ← FALSE_POSITIVE · $note"
      continue
    fi
    # note는 --note-file로 넘긴다 — 한글·특수문자가 Windows argv에서 깨지는 것 회피(DRIVER.md 참조).
    # 경로는 repo 안(build/)에 만든다: mktemp의 MSYS 경로(/tmp/...)는 JVM이 C:\tmp\...로 읽어 실패한다.
    notefile="${ROOT}/build/review-revert-note.txt"
    mkdir -p "$(dirname "$notefile")"
    printf '%s' "$note" > "$notefile"
    if (cd "$ROOT" && ./gradlew -q reviewLesson \
          --args="--rule $rule --kind FALSE_POSITIVE --note-file $notefile" --no-daemon); then
      echo "[revert-lesson] 기록: $rule ← FALSE_POSITIVE"
      recorded=$((recorded+1))
    else
      echo "[revert-lesson] ⚠️ 기록 실패: $rule — 수동 기록 필요"
      failed=1
    fi
    rm -f "$notefile"
  done
  # 실패한 규칙은 lessons에 기록이 안 남았으므로 다음 실행이 자동으로 재시도한다
  # (별도 '처리했음' 표시가 없으니 '실패했는데 처리로 표시돼 신호가 유실'되는 경로 자체가 없다).
  [ "$failed" -eq 1 ] && echo "[revert-lesson] 일부 실패 — 원인 해결 후 재실행하면 남은 것만 기록된다"
done

echo "[revert-lesson] 완료 — 기록 ${recorded}건 · 이미 처리 ${skipped}건$([ "$DRY" -eq 1 ] && echo ' (dry-run)')"
echo "[revert-lesson] 확인: ./gradlew reviewAccuracy"
exit 0
