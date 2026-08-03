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

## 동시 기록 충돌
append-only라 두 브랜치가 각각 교훈을 추가하면 **마지막 줄에서 병합 충돌**이 날 수 있다.
JSONL은 줄 단위 독립이므로 **양쪽 줄을 모두 남기면(keep both)** 해결된다 — 유실 없음.
충돌이 잦아지면 "교훈 1건=1파일"로 전환을 검토(별도 이슈).
