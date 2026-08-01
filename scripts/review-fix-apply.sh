#!/usr/bin/env bash
# AI 코드 리뷰 루프 · 공용 조각 — Minor findings를 "수정 요청서(방안 포함)"로 변환한다.
# 예전엔 여기서 claude CLI를 직접 호출(중첩)했지만, 이제는 호출하지 않는다.
# 드라이버(Claude Code)가 이 요청서를 읽어 사용자와 방안을 확정하고, 자기 Edit 도구로 수정한다.
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

# ── 수정 방안 카탈로그(규칙기반) — 1번이 팀 규칙(CLAUDE.md/rules.yaml) 기준 추천안 ──
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

# ── 요청서 작성 — 드라이버(Claude Code)가 읽고 사용자와 방안 확정 → Edit로 수정 ──
n=0
{
  echo "# 리뷰 루프 · Minor 수정 요청서"
  echo
  echo "> 드라이버(Claude Code)용. 각 항목의 방안을 사용자와 채팅으로 확정한 뒤 **Edit 도구로만** 고친다."
  echo "> 나열된 항목 외 리팩터·무관 변경 금지. 커밋·push는 사용자 승인 후(예산: AutoFix ≤3 · Total ≤6)."
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
    echo "방안(1=추천):"
    i=1; for o in "${opts[@]}"; do echo "  $i) $o"; i=$((i+1)); done
    echo "  s) 건너뛰기"
    echo
  done < "$FINDINGS"
} > "$REQUEST"

echo "[reviewFix] 수정 요청서 ${n}건 → $REQUEST"
echo "[reviewFix] 다음: Claude Code에게 '이 요청서 처리해' — 방안 확정 후 Edit로 수정합니다."
exit 0
