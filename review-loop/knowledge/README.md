# review-loop / knowledge

리뷰 루프의 **영속·공유 지식**. `logs/`(휴발성·`.gitignore` 무시)와 달리 이 폴더는 **추적(커밋)**한다.

## lessons.jsonl
사람 정정 교훈(append-only JSONL). 한 줄 = 한 교훈:
```json
{"timestamp":"...","ruleId":"CONV_001","kind":"FALSE_POSITIVE","humanNote":"재사용 대상 없어 오탐"}
```
- `kind`: `FALSE_POSITIVE`(Judge가 오판) / `MISSED`(Judge가 놓침)
- 기록: `./gradlew reviewLesson --args="--rule <R> --kind FALSE_POSITIVE --note '<근거>'"`
- 다음 판정부터 `ReviewLoop`이 프롬프트에 실어 반영한다. CI(gate2)도 커밋된 이 파일을 로드한다.
- 집계: `./gradlew reviewAccuracy` (규칙별 오탐/누락 횟수)

## loop-metrics.jsonl
**루프 자신의** 지표 기준선(append-only JSONL). 한 줄 = 한 시점의 원시 카운터:
```json
{"timestamp":"...","judged":119,"withFinding":8,"findings":8,"skipRuns":44,"unreviewed":512,"humanJudged":1}
```
- 기록: `./gradlew reviewOptimize --args="--snapshot"` · 조회: `./gradlew reviewOptimize`
  (조회 시 이 파일의 최신 줄이 `이전` 칸이 되고, `변화` 칸에 차이가 %p로 찍힌다)
- **비율은 적지 않는다** — 커버리지·수율·전환율은 읽을 때 계산한다. 비율을 파일에 남기면 정의를 바꿨을 때
  과거 줄과 새 줄의 뜻이 달라지고, 그 차이는 파일만 봐서는 보이지 않는다.
- 최신 줄 판정은 **파일 위치가 아니라 timestamp** 기준(union 머지가 순서를 흔든다).
- 여기(`knowledge/`) 있는 이유: 판정 결과가 아니라 **루프의 상태**이고, 브랜치별로 기준선이 갈리면
  "개선됐나"의 답이 사람마다 달라진다. 설계: [UNIFIED_DESIGN.md](../UNIFIED_DESIGN.md) §9

## 동시 기록 충돌
append-only라 두 브랜치가 각각 교훈을 추가하면 **마지막 줄에서 병합 충돌**이 날 수 있다.
JSONL은 줄 단위 독립이므로 **양쪽 줄을 모두 남기면(keep both)** 해결된다 — 유실 없음.
충돌이 잦아지면 "교훈 1건=1파일"로 전환을 검토(별도 이슈).
