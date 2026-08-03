# 리뷰 루프 · 드라이버(Claude Code) 플레이북

훅은 **순수 게이트**다. 수정·커밋·재push·예산은 **드라이버(Claude Code)가 대화 안에서** 수행한다.
훅/스크립트 안에서 `claude`를 부르지 않는다(중첩 금지).

## 역할 분리
| 주체 | 하는 일 | 안 하는 일 |
|---|---|---|
| pre-push 훅 | Gate1(ArchUnit)·Gate2(LLM Judge) 판정. Critical/미완성이면 exit 1. Minor는 요청서만 남기고 통과. | 코드 수정·커밋·push·claude 호출 |
| 드라이버(Claude Code) | 요청서를 읽어 사용자와 방안 확정 → Edit 수정 → 커밋 → 재push. 예산 통제. | Critical/미완성 임의 수정 |

## 트리거
- 사용자가 "push 해" → Claude가 `git push` 실행 → pre-push 훅이 게이트.
- 훅이 **Critical/미완성으로 exit 1** → push 중단. Claude는 사용자에게 보고하고 **사람 판단**을 받는다(자동수정 금지).
- 훅이 통과하며 **Minor 요청서**(`<git-dir>/review-fix-request.md`)를 남기면 → 아래 루프.

## 드라이버 루프 (예산 통제)
1. `<git-dir>/review-fix-request.md` 를 읽어 findings + 방안을 **사용자에게 채팅으로 제시**한다.
2. 사용자가 항목별 방안을 고른다(기본 1=추천, s=건너뛰기).
   - **s=건너뛰기(오탐)로 판정되면** → 오탐으로 기록(다음 판정이 같은 오탐을 반복하지 않게):
     `./gradlew reviewLesson --args="--rule <RULE> --kind FALSE_POSITIVE --note '<한 줄 근거>'"`
   - **방안을 골라 수정하면(=Judge가 옳았음)** → 확정으로 기록(오탐률의 분모를 채워 규칙 정확도 신호를 만듦):
     `./gradlew reviewLesson --args="--rule <RULE> --kind CONFIRMED --note '<무엇을 고쳤는지>'"`
   - 규칙 정확도 조회: `./gradlew reviewAccuracy` (오탐률 높은 규칙 = 프롬프트 개선 후보).
   - note에 특수문자(`—`·따옴표 등)가 있으면 `--note` 대신 `--note-file <UTF-8 경로>`로 — Windows argv 인코딩 깨짐 회피.
3. **예산 확인** — 수정 라운드 시작 전 `./gradlew reviewBudget --args="--inc-autofix"`.
   출력에 `⚠️ 한도 초과`가 있으면 더 진행하지 말고 **종료·사람 인계**(아래 6).
4. 확정된 방안대로 **Edit 도구로만** 수정한다. 나열된 항목 외 리팩터·무관 변경 금지.
5. `git diff` 를 사용자에게 보여주고 **커밋 승인**을 받는다(승인 없이 commit/push 금지).
6. 커밋 → `git push` 재시도 전 `./gradlew reviewBudget --args="--inc-total"`. `⚠️ 한도 초과`면 재push 말고 종료.
7. **예산**:
   - AutoFix(수정 라운드) ≤ 3, Total(push 재시도) ≤ 6 — 카운터 `.git/reviewloop-budget`(로컬·브랜치별, HEAD 브랜치 바뀌면 자동 리셋).
   - 초과 시: `review-loop/logs/error_log.jsonl` 에 남기고 **종료**(사람에게 인계). 새 작업 시작 시 `--reset`.
   - 예산은 드라이버 소유(훅 아님). gradle이 exit code를 감싸므로 **출력의 `한도 초과` 표시**로 판단.

## 불변 규칙
- 훅·스크립트는 claude를 호출하지 않는다. 루프의 주체는 훅 바깥의 드라이버다.
- Minor는 push를 막지 않는다(러너는 Critical/미완성만 차단). 요청서는 "다음에 고칠 것" 안내다.
- Critical/미완성은 항상 사람. 드라이버가 임의로 고치지 않는다.
- 급할 때 우회: `git push --no-verify` — **로컬 훅만** 건너뛴다. 머지 전 PR에서 서버 게이트가 다시 잡는다:
  | CI 잡 | 무엇을 보나 | 머지 차단 |
  |---|---|---|
  | `semgrep-query` (Gate 1) | PR 코드의 신규 `@Query` | ✅ |
  | `gate2-deterministic` | **루프 자신의** 채점·근거검증 로직 (PR 코드 아님) | ✅ |
  | `gate2-review` | **PR 코드를 LLM 판정** — 훅과 동일한 `reviewLoop --gate` | ✅ Critical·미완성만 |
  | `gate2-live-judge` | golden 씨앗 기준 어댑터 회귀(스모크, PR 코드 아님) | ❌ informational |

  → `--no-verify`로 밀어도 **Critical/미완성은 PR에서 차단**된다. Minor는 로컬·CI 모두 차단하지 않는다(요청서만).
  → 단, 이 차단이 실제로 강제되려면 **GitHub 브랜치 보호에서 "Require status checks to pass"가 켜져 있어야 한다.**
    꺼져 있으면 빨간 체크로도 머지된다(저장소 Settings → Branches)..
