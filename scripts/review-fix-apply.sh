#!/usr/bin/env bash
# AI 코드 리뷰 루프 · 공용 조각 — Minor findings를 "수정 요청서(방안 포함)"로 변환한다.
# 예전엔 여기서 claude CLI를 직접 호출(중첩)했지만, 이제는 호출하지 않는다.
# 드라이버(Claude Code)가 이 요청서를 읽어 Edit 도구로 일괄 수정하고, 검증 후 diff를 사용자에게 제시한다.
# review-fix.sh(수동)와 .githooks/pre-push(push 시)가 함께 쓴다(방안 카탈로그 중복 방지).
#
# 사용: bash scripts/review-fix-apply.sh <findings-file> [request-out]
#   findings-file 각 줄: 'path:line [RULE] 설명'
#   request-out(선택): 요청서 출력 경로(기본 <git-dir>/review-fix-request.md)
# 반환: 0 = 요청서 생성(또는 findings 없음). 이 스크립트는 코드를 절대 건드리지 않는다.
set -u

FINDINGS="${1:-}"
REQUEST="${2:-$(git rev-parse --git-dir)/review-fix-request.md}"

if [ -z "$FINDINGS" ] || [ ! -s "$FINDINGS" ]; then
  echo "[reviewFix] 적용할 findings 없음 → 요청서 생성 안 함"; exit 0
fi

# ── 수정 방안 카탈로그(규칙기반) — 1번이 팀 규칙(CLAUDE.md/rules.yaml) 기준 표준안 ──
# 사전 질문용이 아니다(통합 설계 §5에서 사전 질문 폐지). 드라이버가 "어느 방향으로 고칠지" 참조하는 표준이다.
options_for() {
  case "$1" in
    PERF_001)  printf '%s\n' \
      "fetch join으로 단일 연관을 한 번에 조회 (단일 연관 · 추천)" \
      "@BatchSize로 컬렉션/다중 연관 배치 로딩 (다중)" \
      "count 등 집계는 비정규화 컬럼 사용 (ADR 확인 · 집계)" ;;
    CONV_001)  printf '%s\n' \
      "기존 global 공통 유틸로 교체 (ClockConfig·S3UrlPresigner 등 · 추천)" \
      "중복 유틸 제거 후 호출부를 표준 유틸로 수정" ;;
    ARCH_003a) printf '%s\n' \
      "읽기 전용 투영(read model)으로 전환 (추천)" \
      "포트-어댑터(인터페이스) 도입으로 의존 역전" ;;
    *)         printf '%s\n' \
      "Claude 자동 판단으로 최소 수정 (추천)" ;;
  esac
}

# ── 요청서 작성 — 드라이버(Claude Code)가 읽고 Edit로 일괄 수정 → 검증 → diff 항목별 승인 ──
n=0
{
  echo "# 리뷰 루프 · Minor 수정 요청서"
  echo
  echo "> 드라이버(Claude Code)용. 아래 항목을 **Edit 도구로만** 일괄 수정한다(사전 항목별 질문 없음)."
  echo "> 나열된 항목 외 리팩터·무관 변경 금지."
  echo ">"
  echo "> 1. 예산: \`./gradlew reviewBudget --args=\"--inc-autofix\"\` (AutoFix ≤3 · Total ≤6)"
  echo "> 2. 수정 후 검증: \`bash scripts/review-verify.sh\`  → 실패하면 로그 보고 재수정(예산 무소모)"
  echo "> 3. 재판정 PASS 후 최종: \`bash scripts/review-verify.sh --with-test\`"
  echo "> 4. \`git diff\`를 **항목별로 묶어** 제시 → 사용자 승인 → 그 자리에서 교훈 기록:"
  echo ">    되돌림 → \`reviewLesson --kind FALSE_POSITIVE\` · 수락 → \`reviewLesson --kind CONFIRMED\`"
  echo "> 5. 커밋 → \`reviewBudget --args=\"--inc-total\"\` → 재push"
  echo ">"
  echo "> 절차 상세: review-loop/DRIVER.md"
  echo
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    n=$((n+1))
    rule="$(printf '%s' "$line" | grep -oE '\[[A-Z0-9_]+\]' | head -1 | tr -d '[]')"
    # mapfile은 bash 4+ 전용 — macOS 기본 bash 3.2에서 훅이 깨진다. 이식성 있는 방식으로 채운다.
    opts=()
    while IFS= read -r o; do opts+=("$o"); done < <(options_for "$rule")
    echo "## $n. $line"
    echo
    echo "수정 방향(1=팀 규칙 기준 표준안):"
    i=1; for o in "${opts[@]}"; do echo "  $i) $o"; i=$((i+1)); done
    echo
    # 학습 루프 쓰기 쪽 — 규칙 ID를 이미 알고 있으니 '복붙 가능한 명령'까지 만들어 준다.
    # 안내문만 있으면 기록이 빠진다(실제로 lessons.jsonl이 0건이었다). 마찰을 없애는 게 유일한 방법.
    if [ -n "$rule" ]; then
      echo "diff 승인 시 교훈 기록(8번 단계 · 둘 중 하나):"
      echo "  수락  ./gradlew reviewLesson --args=\"--rule $rule --kind CONFIRMED --note '무엇을 고쳤는지'\""
      echo "  되돌림 ./gradlew reviewLesson --args=\"--rule $rule --kind FALSE_POSITIVE --note '왜 오탐인지'\""
      echo
    fi
  done < "$FINDINGS"
} > "$REQUEST"

echo "[reviewFix] 수정 요청서 ${n}건 → $REQUEST"
echo "[reviewFix] 다음: Claude Code에게 '이 요청서 처리해' — Edit 수정 → 검증 → diff 승인 순으로 진행합니다."
exit 0
