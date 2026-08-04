#!/usr/bin/env bash
# AI 코드 리뷰 루프 · revert된 수정 → FALSE_POSITIVE 후보 보고 (P4 · UNIFIED_DESIGN.md §8)
#
# ── 왜 '자동 기록'이 아니라 '후보 보고'인가 ─────────────────────────────────
# 초안 P4는 "커밋이 revert되거나 **CI 실패 시** FALSE_POSITIVE 자동 기록"이었다.
# CI 실패는 근거가 안 된다 — 그건 보통 "우리 수정이 틀렸다"는 신호이지 "Judge의 지적이 오탐이었다"는
# 신호가 아니다. 잘못된 교훈이 판정 프롬프트에 주입되면 Judge가 진짜 위반을 놓치기 시작한다.
#
# 그런데 **revert도 같은 약점이 있다**(CodeRabbit 지적, PR #18): 지적 자체는 옳았는데 수정 구현이
# 회귀를 내서 revert하는 경우가 있고, 한 커밋이 여러 규칙을 고쳤으면 그 규칙 전부가 오탐으로 찍힌다.
# revert는 "오탐일 수 있다"는 강한 힌트일 뿐 확정 근거가 아니다.
#
# 그래서 기본 동작은 **후보를 보여주는 것**이고, 기록은 사람의 명시적 확인이 있을 때만 한다:
#   ① revert 커밋 메시지에 확인 트레일러를 남긴다:  Review-Lesson: FALSE_POSITIVE
#      (revert 후 `git commit --amend`로 한 줄 추가하거나, revert 시 -e로 편집)
#   ② --apply 로 실행한다
# 둘 중 하나라도 없으면 기록하지 않고 복붙용 명령만 출력한다.
#
# 사용: bash scripts/review-lesson-from-revert.sh [--apply] [--since <ref>]
#   (기본)   후보만 출력 — 아무것도 기록하지 않는다
#   --apply  확인 트레일러가 있는 revert만 기록한다
#   --since  검사 시작 지점(기본: 최근 200커밋)
# 필요: review-loop/logs/fix-trail.jsonl (scripts/review-trail.sh가 커밋 시 남긴다)
# 반환: 0 = 완료. 1 = 기록 시도 중 일부 실패. 2 = 인자·전제 오류.
set -u

APPLY=0
SINCE=""
CONFIRM_TRAILER="Review-Lesson: FALSE_POSITIVE"
while [ $# -gt 0 ]; do
  case "$1" in
    --apply) APPLY=1; shift ;;
    --dry-run) shift ;;   # 하위호환 — 이제 '보고'가 기본이라 무동작
    --since)
      # 값이 없으면 shift 2가 실패하고 $#가 그대로 남아 while이 영원히 돈다(set -e 없음).
      if [ $# -lt 2 ] || [ -z "$2" ]; then
        echo "[revert-lesson] --since 에 ref 값이 필요합니다"; exit 2
      fi
      SINCE="$2"; shift 2 ;;
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
candidates=0
any_failed=0

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

  # 사람의 명시적 확인 — revert 커밋 메시지의 트레일러. 없으면 기록하지 않는다.
  # revert 이유가 '지적이 틀렸다'인지 '수정 구현이 틀렸다'인지는 사람만 안다.
  confirmed=0
  if git log -1 --format=%B "$revert_sha" | grep -qF "$CONFIRM_TRAILER"; then
    confirmed=1
  fi

  failed=0
  for rule in $rules; do
    note="[${MARK} ${revert_sha:0:8}→${full:0:8}] revert + 사람 확인(${CONFIRM_TRAILER})"

    # 이미 이 (revert, rule) 조합이 lessons에 있으면 건너뛴다 — 공유 파일이 유일한 진실.
    if [ -f "$LESSONS" ] \
       && grep -F "${MARK} ${revert_sha:0:8}" "$LESSONS" | grep -qF "\"ruleId\":\"$rule\""; then
      skipped=$((skipped+1)); continue
    fi

    if [ "$APPLY" -eq 0 ] || [ "$confirmed" -eq 0 ]; then
      # 후보 보고 — 사람이 판단해 복붙하거나, 확인 트레일러를 달고 --apply로 다시 돌린다.
      candidates=$((candidates+1))
      echo "[revert-lesson] 후보: $rule  (revert ${revert_sha:0:8} → ${full:0:8})"
      if [ "$confirmed" -eq 0 ]; then
        echo "    └ 사람 확인 없음 — 이 지적이 실제로 오탐이었다면 revert 커밋 메시지에"
        echo "      '${CONFIRM_TRAILER}' 한 줄을 넣고 --apply 로 다시 실행하세요."
      fi
      echo "    └ 지금 바로 기록하려면:"
      echo "      ./gradlew reviewLesson --args=\"--rule $rule --kind FALSE_POSITIVE --note '오탐 근거'\""
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
      any_failed=1
    fi
    rm -f "$notefile"
  done
  # 실패한 규칙은 lessons에 기록이 안 남았으므로 다음 실행이 자동으로 재시도한다
  # (별도 '처리했음' 표시가 없으니 '실패했는데 처리로 표시돼 신호가 유실'되는 경로 자체가 없다).
  [ "$failed" -eq 1 ] && echo "[revert-lesson] 일부 실패 — 원인 해결 후 재실행하면 남은 것만 기록된다"
done

echo "[revert-lesson] 완료 — 기록 ${recorded}건 · 후보 ${candidates}건 · 이미 처리 ${skipped}건"
if [ "$APPLY" -eq 0 ] && [ "$candidates" -gt 0 ]; then
  echo "[revert-lesson] 보고 전용 모드다(아무것도 기록하지 않았다). 기록하려면 위 안내대로 --apply."
fi
echo "[revert-lesson] 확인: ./gradlew reviewAccuracy"

# 기록을 시도했는데 실패한 게 있으면 실패로 끝낸다 — 주기 실행·호출 스크립트가 성공으로 오독하면 안 된다.
if [ "$any_failed" -eq 1 ]; then
  echo "[revert-lesson] ❌ 일부 기록 실패 → exit 1"
  exit 1
fi
exit 0
