# 리뷰 루프 · 드라이버(Claude Code) 플레이북

훅은 **순수 게이트**다. 수정·검증·커밋·재push·예산은 **드라이버(Claude Code)가 대화 안에서** 수행한다.
훅/스크립트 안에서 `claude`를 부르지 않는다(중첩 금지).

> **불변식** — 찾는 주체와 고치는 주체는 절대 같지 않다.
> 찾기=Gemini · 채점=결정론 코드 · **고치기=Claude Code** · 검증=결정론 코드(`javac`+`test`).
> 설계 근거: [UNIFIED_DESIGN.md](UNIFIED_DESIGN.md)

## 역할 분리
| 주체 | 하는 일 | 안 하는 일 |
|---|---|---|
| pre-push 훅 | Gate1(ArchUnit·**차단**)·Gate2(LLM Judge·**리포터**) 판정. Minor는 요청서만 남기고 통과. | 코드 수정·커밋·push·claude 호출 |
| 드라이버(Claude Code) | 요청서를 읽어 Edit 수정 → 검증 → diff 제시 → 커밋 → 재push. 예산·교훈 기록 통제. | Critical/미완성 임의 수정 |
| 검증(`review-verify.sh`) | `compileJava`+`compileTestJava`, 커밋 직전 `test`. **LLM 없음** — 컴파일러와 테스트만 판단. | 롤백·재수정 판단(드라이버가 맥락 보고 결정) |

## 트리거
- 사용자가 "push 해" → Claude가 `git push` 실행 → pre-push 훅이 게이트.
- 훅이 **Critical/미완성으로 exit 1** → push 중단. Claude는 사용자에게 보고하고 **사람 판단**을 받는다(자동수정 금지).
- 훅이 통과하며 **Minor 요청서**(`<git-dir>/review-fix-request.md`)를 남기면 → 아래 루프.

## 드라이버 루프 (통합 · 예산 통제)

```
0. (선택) 격리     bash scripts/review-session.sh          # worktree+브랜치. 큰 체인지셋일 때만
1. 판정            ./gradlew reviewLoop --args="--files-from <changed> --findings-out <minor>"
2. 요청서          bash scripts/review-fix-apply.sh <minor> <request>
3. 예산            ./gradlew reviewBudget --args="--inc-autofix"      # ⚠️ 한도 초과면 종료·인계
4. 수정            Claude Code — Edit 도구로만. 나열 항목 외 리팩터 금지
5. 검증            bash scripts/review-verify.sh                      # 컴파일. 실패 → 4로(예산 무소모)
6. 재판정          1로 복귀. PASS면 7, NEEDS_REVISION이면 3으로(budget 내)
7. 최종 검증       bash scripts/review-verify.sh --with-test           # 커밋 직전 1회
8. 승인·교훈       git diff를 항목별로 제시 → 사람 승인 → 교훈 기록 → 커밋
9. 재push          ./gradlew reviewBudget --args="--inc-total"  후 git push
```

훅이 이미 1·2를 해준 경우(요청서가 있는 경우)에는 **3번부터** 시작한다.

### 1~2 · 판정과 요청서
훅 없이 손으로 돌릴 때는 두 단계를 한 번에: `bash scripts/review-fix.sh --files-from <changed>`.
요청서에는 규칙별 **수정 방안 카탈로그**가 붙는다(1=팀 규칙 기준 추천안). 4번에서 어느 방향으로 고칠지
**참조하는 표준**으로 쓴다 — 사전에 사용자에게 방안을 묻는 용도가 아니다(§8에서 diff로 판단).

### 3 · 예산
수정 라운드 시작 전 `./gradlew reviewBudget --args="--inc-autofix"`.
출력에 `⚠️ 한도 초과`가 있으면 더 진행하지 말고 **종료·사람 인계**.

### 4 · 수정
요청서 항목만 **Edit 도구로** 고친다. 나열된 항목 외 리팩터·무관 변경 금지.
Critical/미완성은 여기서 고치지 않는다 — 사람에게 보고하고 멈춘다.

### 5 · 검증 (신규)
```bash
bash scripts/review-verify.sh
```
전체 `compileJava`+`compileTestJava`. 단일 파일 컴파일이 아니라 전체라서 **다른 파일을 깨뜨린 수정(교차 파괴)**을
잡는다. 실패하면 로그를 읽고 **4번으로 돌아가 재수정** — 자동 롤백하지 않는다(수정 주체가 맥락을 아는 쪽이 낫다).

**검증 실패는 예산을 소모하지 않는다.** 새 라운드가 아니라 같은 라운드 안의 재시도다.

### 6 · 재판정
1번으로 복귀해 다시 판정한다. PASS면 7번, `NEEDS_REVISION`이면 3번(예산 증가)부터 다시.

### 7 · 최종 검증
```bash
bash scripts/review-verify.sh --with-test
```
전체 테스트까지. **커밋 직전 1회만** 돈다(라운드마다 돌리면 느리다).

### 8 · 승인 + 교훈 기록 (같은 시점)
`git diff`를 **요청서 항목별로 묶어** 사용자에게 제시하고, 항목 단위로 수락/되돌림을 받는다.
승인 없이 commit/push 금지. 그 자리에서 학습 신호를 기록한다:

| 사용자 판단 | 의미 | 기록 |
|---|---|---|
| 되돌림 | Judge 오탐 | `./gradlew reviewLesson --args="--rule <RULE> --kind FALSE_POSITIVE --note '<한 줄 근거>'"` |
| 수락 | Judge가 옳았음 | `./gradlew reviewLesson --args="--rule <RULE> --kind CONFIRMED --note '<무엇을 고쳤는지>'"` |

- 수정 **전에** 항목마다 방안을 묻지 않는다. 일괄 수정 후 **실제 diff를 보고** 판단하므로 사람의 판단 근거가 더 좋다.
- 기록된 교훈은 다음 판정 프롬프트에 자동 주입된다(`JudgePromptBuilder`) — 같은 오탐이 반복되지 않는다.
- **요청서에 항목별 복붙용 명령이 들어 있다**(규칙 ID가 채워진 상태). 안내문만 있으면 기록이 빠진다.
- 규칙 정확도 조회: `./gradlew reviewAccuracy` (오탐률 높은 규칙 = 프롬프트 개선 후보).
- note에 특수문자(`—`·따옴표 등)가 있으면 `--note` 대신 `--note-file <UTF-8 경로>`로 — Windows argv 인코딩 깨짐 회피.

> **이 단계를 건너뛰면 루프가 학습하지 않는다.**
> 교훈을 **읽는 쪽은 코드가 자동**으로 한다 — 판정마다 `lessons.jsonl`을 읽어 판정 프롬프트에 붙인다
> (`ReviewLoopRunner:121` → `JudgePromptBuilder.buildPolicy`). 반면 **쓰는 쪽은 사람이 직접** `reviewLesson`을
> 쳐야 한다(자동 적재는 revert 회수 하나뿐 · §아래).
> 프롬프트로 넣어주는 관은 깔려 있는데 부어줄 물이 없는 구조라, 기록을 빼먹으면 `lessons.jsonl`이 0건으로
> 남고 Judge는 매번 빈 교훈 목록을 받는다(실제로 오래 0건이었다). `reviewLoop`·`reviewAccuracy`가 0건일 때
> 경고를 출력하니, 그 경고가 보이면 이 단계가 빠진 것이다.

**커밋 직후 이력 기록** — 나중에 이 수정이 revert되면 자동으로 오탐을 잡아낼 수 있게 매핑을 남긴다:

```bash
bash scripts/review-trail.sh <minor-findings-file>
```

`review-loop/logs/fix-trail.jsonl`에 `{커밋, 규칙, findings 수}`가 한 줄 추가된다(같은 커밋 재호출은 무해).
이 매핑이 없으면 revert가 생겨도 **어느 규칙이 오탐이었는지 특정할 수 없다.**

### 자동 오탐 회수 (revert 기반)

누군가 리뷰 수정 커밋을 `git revert`하면 — 사람이 "이 지적의 수정은 하지 말았어야 했다"고 명시적으로
뒤집은 것이므로 오탐의 근거가 된다. 주기적으로(또는 revert 직후) 돌린다:

```bash
bash scripts/review-lesson-from-revert.sh --dry-run
```

`--dry-run`으로 무엇이 기록될지 먼저 보고, 맞으면 플래그 없이 실행한다.
멱등성 근거는 `lessons.jsonl` 자신이다(note의 `[auto:revert <sha>→<sha>]` 토큰) — 팀 공유 파일이라
클론이 여러 개여도 같은 revert가 중복 기록되지 않는다.

> **CI 실패는 근거로 쓰지 않는다.** 설계 초안(P4)은 "revert **또는 CI 실패** 시 자동 기록"이었지만,
> CI 실패는 보통 *우리 수정이 틀렸다*는 신호이지 *Judge의 지적이 오탐이었다*는 신호가 아니다.
> 그걸로 오탐을 적재하면 잘못된 교훈이 프롬프트에 주입돼 **Judge가 진짜 위반을 놓치기 시작한다** —
> 학습 루프가 스스로를 망가뜨린다. 그래서 revert만 쓴다.
> 손으로 되돌린 커밋(`This reverts commit` 표식 없음)은 잡히지 않는다 — 그건 수동 기록한다.

### 9 · 재push
커밋 → `git push` 재시도 **전에** `./gradlew reviewBudget --args="--inc-total"`. `⚠️ 한도 초과`면 재push 말고 종료.

## 예산
- AutoFix(수정 라운드) ≤ 3, Total(push 재시도) ≤ 6 — 카운터 `.git/reviewloop-budget`(로컬·브랜치별, HEAD 브랜치 바뀌면 자동 리셋).
- 5번 검증 실패는 **카운트하지 않는다**(같은 라운드 내 재시도).
- 초과 시: `review-loop/logs/error_log.jsonl` 에 남기고 **종료**(사람에게 인계). 새 작업 시작 시 `--reset`.
- 예산은 드라이버 소유(훅 아님). gradle이 exit code를 감싸므로 **출력의 `한도 초과` 표시**로 판단.

## 격리(worktree)는 선택
`bash scripts/review-session.sh` — worktree + 브랜치 + 변경 .java 목록까지만 준비하고 끝난다(루프를 돌리지 않는다).
사람이 `git diff`로 승인하므로 격리는 필수가 아니다. 다음일 때만 쓴다:
- 변경 파일이 많아 메인 트리를 깨끗이 두고 싶을 때
- 실험적 수정을 버릴 가능성이 있을 때

## 불변 규칙
- 훅·스크립트는 claude를 호출하지 않는다. 루프의 주체는 훅 바깥의 드라이버다.
- 판정(Gemini)과 수정(Claude Code)은 **다른 주체**다. 자기 승인 금지 — 무인 자율 수정 경로(`reviewAutoFix`)는 휴면.
- Minor는 push를 막지 않는다(러너는 Critical/미완성만 차단). 요청서는 "다음에 고칠 것" 안내다.
- Critical/미완성은 항상 사람. 드라이버가 임의로 고치지 않는다.
- 급할 때 우회: `git push --no-verify` — **로컬 훅만** 건너뛴다. 머지 전 PR에서 서버 게이트가 다시 잡는다:
  | CI 잡 | 무엇을 보나 | 머지 차단 |
  |---|---|---|
  | `semgrep-query` (Gate 1) | PR 코드의 신규 `@Query` | ✅ |
  | `gate2-deterministic` | **루프 자신의** 채점·근거검증 로직 (PR 코드 아님) | ✅ |
  | `gate2-review` | **PR 코드를 LLM 판정** — 훅과 동일한 `reviewLoop --gate` | ❌ 리포터(아래 정책) |
  | `gate2-live-judge` | golden 씨앗 기준 어댑터 회귀(스모크, PR 코드 아님) | ❌ informational |

  → Minor는 로컬·CI 모두 차단하지 않는다(요청서만).
  → 단, 차단이 실제로 강제되려면 **GitHub 브랜치 보호에서 "Require status checks to pass"가 켜져 있어야 한다.**
    꺼져 있으면 빨간 체크로도 머지된다(저장소 Settings → Branches).

### 정책: Gate 2 = 리포터, 차단은 결정론 게이트 몫

**확정(2026-08-03)**: LLM 판정은 **막지 않는다.** `push`·머지를 막는 건 결정론 게이트(Gate 1 ArchUnit ·
semgrep)뿐이다. 이유는 두 가지다 — 팀 원칙("Minor는 push를 막지 않는다")과 일관되고, LLM 오탐 1건이
팀 전체의 push를 막는 상황을 만들지 않는다.

이건 선언이 아니라 **현재 코드의 실제 동작**이다. `--gate`는 `INCOMPLETE`·`AWAITING_HUMAN`에서만 차단하는데
현 카탈로그에서 둘 다 도달 불가다:

| 차단 결정 | 필요 조건 | 현재 상태 |
|---|---|---|
| `AWAITING_HUMAN` | CRITICAL finding | judge 규칙 3개가 **전부 MINOR**. `normalize()`가 LLM severity를 카탈로그 값으로 덮으므로 CRITICAL 생성 불가 |
| `INCOMPLETE` | `FindingSource.ACCEPTANCE` finding | 그 소스를 만드는 **프로덕션 경로가 없다**(미배선) |

러너가 `--gate` 실행마다 `역할 : 리포터 — 차단 규칙 0개`를 출력하고, `PrePushGatePolicyTest`가 도달 불가를
고정한다. **정책을 뒤집으려면** `rules.yaml`에 `severity: CRITICAL` judge 규칙을 추가하면 된다(가중치는
P0-a 이후 yaml이 SSOT라 코드 수정 불필요) — 그러면 그 테스트가 실패하며 이 문서 갱신을 요구한다.
배경: [UNIFIED_DESIGN.md](UNIFIED_DESIGN.md) §8.

### 게이트가 막았을 때 — 사유를 먼저 볼 것

훅은 두 경우를 **다르게** 말한다. 섞어 읽으면 안 된다:

| 훅 메시지 | 뜻 | 드라이버가 할 일 |
|---|---|---|
| `❌ Gate 2 차단 결정(Critical/미완성)` | **코드 판정** | 사람에게 보고. 임의 수정 금지 |
| `❌ Gate 2 게이트 오류` / `실행 실패` | **리뷰가 수행되지 않았다** | 원인(키 형식·네트워크·컴파일) 해결 후 재시도. 코드 문제로 오해 금지 |

`--no-verify`로 밀면 후자의 경우 **그 변경은 LLM 판정을 한 번도 받지 않고 나간다.**
