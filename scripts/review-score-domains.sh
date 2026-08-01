#!/usr/bin/env bash
# AI 코드 리뷰 루프 · 도메인 점수 측정 래퍼 — 리팩토링 대상 랭킹 (코어 무수정)
#
# 기존 reviewLoop(Gate 2)를 도메인마다 호출해 파일 점수를 집계하고, git churn(최근 3개월
# 변경 빈도)과 교차해 "리팩토링 대상" 랭킹을 만든다. 루프 코어·rules.yaml·훅은 건드리지
# 않는 순수 래퍼다 — 이 점수는 의미규칙(judge) 위반 밀도이지 종합 품질 점수가 아니다.
#
# 산출물:
#   review-loop/scores/domain-scores.jsonl   누적 측정 기록(1실행×1도메인=1줄) — 추적(커밋) 대상
#   review-loop/scores/report-<RUN_ID>.md    이번 실행 랭킹 리포트 — 추적 대상
#   review-loop/logs/domain-scores/<RUN_ID>/ 도메인별 원본 출력·대상 목록 — 휘발성(gitignore logs/)
#
# 사용: bash scripts/review-score-domains.sh [도메인 ...]     # 생략 시 domain/ 전체
#   SCOPE=core(기본)|all   core = Service/Repository/Query/Policy류만 (LLM 비용 방어)
#   MAX=N                  도메인당 파일 상한(기본: 대상 파일 수 전부)
# 필요: GEMINI_API_KEY (없으면 러너가 Gate 2를 생략해 점수가 안 나온다)
set -u

# Gradle 런처 JVM을 UTF-8로 — review-fix.sh와 동일(Windows 콘솔 cp949 한글 깨짐 방지).
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dfile.encoding=UTF-8"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1

if [ -z "${GEMINI_API_KEY:-}" ]; then
  echo "[domainScore] GEMINI_API_KEY 필요(판정용) — 없으면 점수가 산출되지 않는다"; exit 1
fi

DOMAIN_ROOT="src/main/java/com/wanted/backend/domain"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
RAW_DIR="review-loop/logs/domain-scores/$RUN_ID"
SCORE_DIR="review-loop/scores"
JSONL="$SCORE_DIR/domain-scores.jsonl"
REPORT="$SCORE_DIR/report-$RUN_ID.md"
SCOPE="${SCOPE:-core}"
# core 스코프 — 의미규칙 3개(N+1·유틸 재발명·타도메인 엔티티)가 실제로 사는 파일 유형.
CORE_REGEX='(Service|Repository|Query|Policy|Reader|Writer|UseCase)[A-Za-z0-9]*\.java$'

mkdir -p "$RAW_DIR" "$SCORE_DIR"
SUMMARY="$RAW_DIR/summary.tsv"
: > "$SUMMARY"

# 대상 도메인: 인자로 받거나, 없으면 domain/ 아래 전부.
if [ "$#" -gt 0 ]; then
  DOMAINS=("$@")
else
  DOMAINS=()
  for dir in "$DOMAIN_ROOT"/*/; do
    DOMAINS+=("$(basename "$dir")")
  done
fi

echo "[domainScore] RUN_ID=$RUN_ID · scope=$SCOPE · 도메인 ${#DOMAINS[@]}개"

for d in "${DOMAINS[@]}"; do
  if [ ! -d "$DOMAIN_ROOT/$d" ]; then
    echo "[domainScore] $d — 도메인 디렉터리 없음, 스킵"; continue
  fi

  list="$RAW_DIR/$d.files"
  find "$DOMAIN_ROOT/$d" -name '*.java' | sort > "$list.all"
  if [ "$SCOPE" = "core" ]; then
    grep -E "$CORE_REGEX" "$list.all" > "$list" || true
  else
    cp "$list.all" "$list"
  fi

  count=$(grep -c . "$list" || true)
  if [ "$count" -eq 0 ]; then
    echo "[domainScore] $d — 대상 파일 0개(scope=$SCOPE), 스킵"; continue
  fi
  max="${MAX:-$count}"

  echo "[domainScore] $d — ${count}개 파일 리뷰 중..."
  out="$RAW_DIR/$d.out"
  if ! ./gradlew -q reviewLoop --args="--files-from $list --domain $d --max $max" > "$out" 2>&1; then
    echo "[domainScore] $d — reviewLoop 실패, 스킵 (원본: $out)"; continue
  fi

  # 러너 출력의 파일 점수 라인('… → score N · DECISION · findings M')만 집계.
  eval "$(awk '
    / score / {
      files++
      for (i = 1; i <= NF; i++) {
        if ($i == "score")    { s = $(i+1) + 0; sum += s; if (s < 80) low++ }
        if ($i == "findings") { fnd += $(i+1) + 0 }
      }
      if ($0 ~ /NEEDS_REVISION/) nr++
      if ($0 ~ /AWAITING_HUMAN/) crit++
    }
    END {
      printf "files=%d avg=%s low=%d nr=%d crit=%d fnd=%d\n",
             files, (files ? sprintf("%.1f", sum / files) : 0), low, nr, crit, fnd
    }' "$out")"

  if [ "$files" -eq 0 ]; then
    echo "[domainScore] $d — 점수 라인 없음(키 부재/판정 실패?), 스킵 (원본: $out)"; continue
  fi

  # churn = 최근 3개월 커밋이 이 도메인의 .java를 건드린 횟수(파일 단위 누적).
  churn=$(git log --since=3.months --pretty=format: --name-only -- "$DOMAIN_ROOT/$d" | grep -c '\.java$' || true)

  printf '%s\t%d\t%s\t%d\t%d\t%d\t%d\t%d\n' \
    "$d" "$files" "$avg" "$low" "$nr" "$crit" "$fnd" "$churn" >> "$SUMMARY"
  printf '{"ts":"%s","run":"%s","domain":"%s","scope":"%s","files":%d,"avg_score":%s,"below80":%d,"needs_revision":%d,"critical":%d,"findings":%d,"churn_3m":%d}\n' \
    "$(date -Iseconds)" "$RUN_ID" "$d" "$SCOPE" "$files" "$avg" "$low" "$nr" "$crit" "$fnd" "$churn" >> "$JSONL"
  echo "[domainScore] $d — avg $avg · 80미달 $low/$files · findings $fnd · churn $churn"
done

if [ ! -s "$SUMMARY" ]; then
  echo "[domainScore] 집계된 도메인 없음 → 리포트 생략"; exit 1
fi

# ── 랭킹 리포트 — 판정: (평균<80 또는 80미달 비율≥30%) AND churn≥중앙값 → 리팩토링 대상 ──
median=$(cut -f8 "$SUMMARY" | sort -n | awk '{ a[NR] = $1 } END {
  if (NR == 0) print 0; else if (NR % 2) print a[(NR+1)/2]; else print (a[NR/2] + a[NR/2+1]) / 2 }')

{
  echo "# 도메인 점수 랭킹 — 리팩토링 대상 선정"
  echo
  echo "- 실행: \`$RUN_ID\` · scope=\`$SCOPE\` · churn 중앙값: $median (최근 3개월)"
  echo "- 점수 = 의미규칙(judge) 위반 밀도(100−Σ감점 · 임계 80) — **종합 품질 점수 아님**"
  echo "- 판정: (평균<80 또는 80미달≥30%) **그리고** churn≥중앙값 → 🔴 리팩토링 대상"
  echo "  · 점수 나쁨+저변경 → 🟡 후순위 · CRITICAL 있으면 점수 무관 사람 검토"
  echo "- 원본 출력: \`$RAW_DIR/\` · 누적 기록: \`$JSONL\`"
  echo
  echo "| 순위 | 도메인 | 파일 | 평균점수 | 80미달 | 재수정 | CRITICAL | findings | churn(3m) | 판정 |"
  echo "|---|---|---|---|---|---|---|---|---|---|"
  sort -t "$(printf '\t')" -k3,3n -k8,8nr "$SUMMARY" | awk -F '\t' -v med="$median" '
    {
      bad = ($3 < 80) || ($4 / $2 >= 0.3)
      if ($6 > 0)           verdict = "⛔ 사람 검토(CRITICAL)"
      else if (bad && $8 >= med) verdict = "🔴 리팩토링 대상"
      else if (bad)          verdict = "🟡 후순위(저변경)"
      else                   verdict = "✅ 양호"
      printf "| %d | %s | %d | %s | %d | %d | %d | %d | %d | %s |\n",
             NR, $1, $2, $3, $4, $5, $6, $7, $8, verdict
    }'
} > "$REPORT"

echo
echo "[domainScore] 리포트 → $REPORT"
echo "[domainScore] 누적 기록 → $JSONL (리팩토링 전후 비교는 run 필드로)"
