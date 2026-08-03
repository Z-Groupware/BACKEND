# 리뷰 루프 · 통합 설계 (UNIFIED)

`DRIVER.md`(사람 드라이버 게이트)와 `AUTOLOOP_DESIGN.md`(자율 수정 루프)를 **하나의 경로로 합친다.**
방향은 "자율 루프에 Claude Code를 넣기"가 **아니라**, **드라이버 루프가 자율 루프의 안전장치를 흡수하고
Gemini fixer를 버리는 것**이다.

---

## 0. 왜 합치나

| 현 문제 | 근거 |
|---|---|
| **자기 승인** — 자율 루프는 Gemini가 찾고 Gemini가 고친다 | `AutoLoopRunner:53,62` (judge·fixer 모두 Gemini, 같은 모델) |
| **Minor 소유권 충돌** — 두 루프가 같은 Minor를 각자 고친다 | 훅은 `review-fix-request.md` 생성, 자율은 `autoloop/*` 브랜치 커밋 |
| **인프라 편재** — 격리·컴파일검증은 자율 루프에만, 사람 판단은 드라이버에만 | `VerifiedFixer`/`CompileVerification`는 자율 전용 |

합치면 셋이 동시에 해소된다.

---

## 1. 불변식 (이 설계의 핵심)

> **찾는 주체와 고치는 주체는 절대 같지 않다.**

| 역할 | 주체 | 성격 |
|---|---|---|
| **찾기(판정)** | Gemini (`GeminiJudgeAdapter`) | LLM · 배치 · gradle 태스크 |
| **채점** | 결정론 코드 (`JudgeScorer`) | LLM 아님 · `rules.yaml` SSOT |
| **고치기** | **Claude Code (드라이버)** | Edit 도구 · 대화 · 사람 승인 |
| **검증** | 결정론 코드 (`javac` + `test`) | LLM 아님 |

기존 `DRIVER.md`의 불변 규칙은 그대로 유지한다:
- 훅·스크립트는 `claude`를 호출하지 않는다 (중첩 금지) — 현재 저장소 전체에 `claude` 호출 0건, 유지.
- Critical/미완성은 항상 사람. 루프가 임의 수정하지 않는다.
- push/PR은 사람 승인.

---

## 2. 제어 반전 (Control Inversion)

자율 루프는 **배치가 운전대를 잡고 fixer를 호출**했다. 통합 루프는 **Claude Code가 운전대를 잡고
판정·검증을 태스크로 호출**한다. 이것이 합침의 본질이다.

```
[AS-IS · 자율]
  배치(AutoFixRunner) ──호출──> Gemini fixer      (자기승인, 사람 없음)

[AS-IS · 드라이버]
  Claude Code ──읽음──> 요청서                     (격리·검증 없음)

[TO-BE · 통합]
  Claude Code(운전)
      │
      ├─① reviewLoop   --files-from --findings-out   → Gemini 판정 (찾기)
      ├─② Edit 수정                                   → 판정자와 독립 ✅
      ├─③ review-verify.sh [--with-test]             → javac + test (검증)
      ├─④ ①로 복귀 (PASS 또는 budget 소진까지)
      └─⑤ git diff 제시 → 사람 승인 → 커밋 → 재push
```

---

## 3. 구성 요소 — 신규 / 개조 / 퇴역

### 3.1 신규: `scripts/review-verify.sh` (P0-b · 구현 완료)

드라이버가 자기 수정을 **결정론으로** 검증할 수단. 자율 루프에만 있던 안전막을 드라이버에 넘긴다.

```bash
bash scripts/review-verify.sh              # 컴파일만 — 라운드 중 빠른 확인
bash scripts/review-verify.sh --with-test  # 전체 테스트까지 — 커밋 직전 1회
```

**설계 변경 두 가지 (초안 대비):**

1. **`ReviewVerifyRunner`(Java) 대신 스크립트.** 초안은 `CompileVerification`(in-JVM javac)을 재사용하려 했으나,
   그 클래스는 *"자율 루프가 gradle 안에서 도니까 중첩 락 회피"* 때문에 생긴 우회였다.
   통합 루프의 드라이버는 **gradle 바깥**에서 돌므로 제약이 사라지고,
   **전체 `compileJava`가 단일 파일 javac보다 엄격히 더 강하다** — 단일 파일 javac는
   "다른 파일을 깨뜨리는 수정(교차 파괴)"을 원천적으로 못 잡는다. 더 약한 검증을 새로 만들 이유가 없다.

2. **gradle 태스크로 감싸지 않는다.** `Exec` 태스크가 bash를 부르고 그 bash가 다시 `./gradlew`를 부르면
   그 자체가 중첩 gradle(프로젝트 락)이다. 드라이버는 bash를 직접 실행하므로 태스크 래퍼가 불필요하다.
   `build.gradle`에 이유를 주석으로 남겼다.

- `--with-test`는 구 `review-autoloop.sh`의 "커밋 직전 `./gradlew test` 1회" 정책을 승계한다
  (P2에서 그 스크립트가 `review-session.sh`로 축소되며 해당 단계는 여기로 넘어왔다).
- 실패 시 exit 1 + 로그 → **Claude Code가 롤백·재수정을 판단**(자동 롤백 아님. 맥락 보고 결정).
- 검증 실패는 **예산을 소모하지 않는다** — 라운드가 아니라 같은 라운드 내 재시도다.

> `VerifiedFixer`의 자동 롤백은 **불필요해진다.** 무인 fixer를 방어하려던 장치인데,
> 이제 수정 주체가 맥락을 아는 Claude Code라 "실패 이유를 보고 다시 고치는" 편이 항상 낫다.
> `CompileVerification`은 휴면 자율 경로용으로 남긴다.

### 3.2 개조: 판정 배치를 `ReviewLoopRunner`로 일원화

`AutoLoopOrchestrator`에서 수정 루프(`AutoFixRunner`·`VerifiedFixer`)를 걷어내면 **남는 건 판정뿐**이고,
그건 `ReviewLoopRunner`가 이미 하는 일이다(`--files-from --gate --findings-out`).

→ **`AutoLoopOrchestrator`의 판정 역할은 `ReviewLoopRunner`에 흡수된다.** 클래스 하나가 통째로 사라진다.

살릴 것 하나: 파일별 부모 디렉터리 기준 `EvidenceValidator` 생성(`AutoLoopOrchestrator:63,67`).
worktree/임시 경로에서도 `file:line` 근거 검증이 맞게 돌게 하는 처리라 `ReviewLoopRunner`로 이식한다.

### 3.3 개조: 가중치를 `rules.yaml`에서 읽기 (P0-a · 구현 완료)

`ReviewLoopRunner`·`AutoLoopRunner`가 **동일하게 3개 규칙만 하드코딩**하고 있었다.

```java
Map.of("CONV_001", 15, "PERF_001", 15, "ARCH_003a", 15)
```

**이것은 실버그가 아니었다 — 정확히 진단하면 드리프트 위험이다.**

`RuleCatalog.fromYaml`은 `enforced_by: judge` 규칙만 싣고, `normalize()`는 카탈로그에 없는 ruleId를
환각으로 보고 버린다(화이트리스트). 따라서 **Judge가 finding을 낼 수 있는 규칙은 그 3개뿐**이고,
나머지 13개(test·archunit·semgrep 집행)는 채점기에 도달조차 하지 않는다.
즉 하드코딩 Map은 현재 카탈로그 기준으로 **완전하고 정확했다.** 오채점은 없었다.

문제는 **미래**다. 누가 `enforced_by: judge` 규칙을 4번째로 추가하면 그 규칙만 Map에 없어서
`judge_default_weight`(15) 대신 severity 기본값(10)으로 **조용히** 떨어진다.
yaml은 15라 하는데 코드는 10을 주는 상태가 아무도 모르게 생긴다. `pass_threshold`도 yaml과
`JudgeScorer.DEFAULT_PASS_THRESHOLD` 상수에 이중으로 존재했다.

**조치(완료):**
- `RuleCatalog`가 `meta.score`를 파싱 → `ScorePolicy(passThreshold, defaultWeightBySeverity, judgeDefaultWeight)`
- `RuleCatalog.effectiveWeights()` — judge 규칙 전부에 대해 `규칙 weight 명시값 > judge_default_weight` 순으로 해결
- 두 러너의 하드코딩 Map 제거 → `catalog.effectiveWeights()` / `catalog.scorePolicy()` 사용
- `meta.score`가 없거나 일부만 있으면 문서화된 `ScorePolicy.DEFAULT`로 폴백(테스트 yaml 방어)
- 규칙 단위 `weight:` 오버라이드를 지원(현재 쓰는 규칙은 없으나 yaml 주석이 약속한 동작)

**검증**: `RuleCatalogScoringTest` 7케이스. 핵심은 *"새 judge 규칙이 코드 수정 없이 가중치를 받는다"*와
*"실제 rules.yaml의 3개 규칙이 종전과 같은 15점을 유지한다"*(회귀 방지).

### 3.4 퇴역(즉시 삭제 아님): 자율 수정 계열

| 클래스 | 처분 | 이유 |
|---|---|---|
| `GeminiCodeFixerAdapter` | **기본 경로에서 제외** | 자기승인 원흉 |
| `AutoFixRunner` | 휴면 | 수정 루프 주체가 Claude Code로 이동 |
| `VerifiedFixer` | 휴면 | 자동 롤백 불필요(3.1 참조) |
| `AutoLoopRunner` / `AutoLoopOrchestrator` | 휴면 | 판정은 `ReviewLoopRunner`가 흡수 |
| `CodeFixerPort` (인터페이스) | **유지** | seam은 남긴다 — 무인 모드 재개 시 필요 |
| `CompileVerification` / `VerificationPort` | 휴면 자율 경로 전용 | `reviewVerify`는 재사용하지 **않는다** — §3.1에서 전체 컴파일 채택(초안의 "승격"은 폐기) |

**삭제하지 않고 휴면**시키는 이유: 테스트가 붙어 있고(`AutoFixRunnerTest` 등 검증된 자산), 무인 모드를
되살릴 여지를 남긴다. 단 `reviewAutoFix` 태스크는 **기본 문서·워크플로에서 내린다.**

무인 모드를 꼭 되살려야 한다면(CI 자동수정 등) 불변식 유지 조건:
> judge를 **`ClaudeJudgeAdapter`로 교차**시킨다. 이미 구현돼 있고 `anthropic-java:2.34.0`도
> 이미 의존성에 있다(`build.gradle:61`, 주석: "Gemini만 쓰면 미사용"). **한 줄 교체로 독립성 확보.**

---

## 4. 드라이버 절차 (통합 루프)

기존 `DRIVER.md` 절차에 **③ 검증**과 **격리(선택)**가 추가된 형태.

```
0. (선택) 격리      bash scripts/review-session.sh          # worktree + 브랜치. 큰 체인지셋일 때만
1. 판정             ./gradlew reviewLoop --args="--files-from <changed> --findings-out <minor>"
2. 요청서           bash scripts/review-fix-apply.sh <minor> <request>
3. 예산 확인        ./gradlew reviewBudget --args="--inc-autofix"     # ⚠️ 한도 초과면 종료·인계
4. 수정             Claude Code — Edit 도구로만. 나열 항목 외 리팩터 금지
5. 검증             bash scripts/review-verify.sh                     # ← 신규(전체 컴파일)
                      실패 → 로그 보고 4로 (같은 라운드, 예산 소모 없음)
6. 재판정           1로 복귀. PASS면 7, NEEDS_REVISION이면 3으로 (budget 내)
7. 최종 검증        bash scripts/review-verify.sh --with-test
8. 승인·커밋        git diff 항목별 제시 → 사람 승인 → 교훈 기록(§5) → 커밋
9. 재push           ./gradlew reviewBudget --args="--inc-total"  후 git push
```

**예산**: 기존 `DriverBudget` 그대로 — AutoFix ≤ 3, Total ≤ 6, `.git/reviewloop-budget`(브랜치별 자동 리셋).
5번 검증 실패는 예산을 소모하지 않는다(라운드가 아니라 같은 라운드 내 재시도).

### 격리(worktree)는 선택이다

`review-autoloop.sh`의 worktree 격리는 **무인 fixer가 작업트리를 망칠까 봐** 있던 장치다.
통합 루프는 사람이 `git diff`를 보고 승인하므로 필수가 아니다. 다음일 때만 쓴다:
- 변경 파일이 많아 메인 트리를 깨끗이 두고 싶을 때
- 실험적 수정을 버릴 가능성이 있을 때

→ `scripts/review-autoloop.sh`를 `scripts/review-session.sh`로 개명·축소(✅ P2 완료):
**worktree 생성 + 브랜치 준비 + 변경 `.java` 목록까지만** 하고, 루프 구동(`reviewAutoFix` 호출)·자동 커밋·`--push`는 제거했다.
브랜치 접두사도 `autoloop/` → `review/`. LLM을 부르지 않으므로 `GEMINI_API_KEY`도 요구하지 않는다.

---

## 5. 학습루프 결합 지점 (중요)

교훈(`lessons.jsonl`)은 **사람의 판정 결정**에서 나온다. 통합하면서 이 신호를 잃지 않아야 한다.

- **읽기 방향은 이미 자동** ✅ — `JudgePromptBuilder.buildPolicy()`가 lessons를 판정 프롬프트에 주입.
- **쓰기 방향은 수동** — 사람이 `./gradlew reviewLesson`을 쳐야 기록.

### 결정: 교훈 수집 지점을 "항목별 사전 질문"에서 **"diff 리뷰 시점"**으로 옮긴다

기존 `DRIVER.md:19-20`은 수정 **전에** 항목마다 사용자에게 방안을 물었다(1=추천, s=건너뛰기).
Minor가 여러 건이면 느리다. 통합 루프는:

1. Claude Code가 Minor를 **일괄 수정**(4번)
2. `git diff`를 **항목별로 묶어** 제시(8번)
3. 사람이 항목 단위로 수락/되돌림 → **그 자리에서 신호 기록**
   - 되돌림 → `reviewLesson --kind FALSE_POSITIVE` (오탐)
   - 수락 → `reviewLesson --kind CONFIRMED` (Judge가 옳았음)

**사전 질문 없이도 학습 신호가 보존된다.** 판단 시점을 "추상적 방안 선택"에서 "실제 diff 확인"으로
옮기므로 사람의 판단 근거가 오히려 좋아진다.

> `review-fix-apply.sh`의 방안 카탈로그(규칙별 추천안)는 **버리지 않는다.** 사전 질문용이 아니라
> Claude Code가 4번에서 **어떤 방향으로 고칠지 참조하는 표준**으로 계속 쓴다.

---

## 6. 단계별 이행

| 단계 | 내용 | 산출물 | 리스크 |
|---|---|---|---|
| ~~**P0-a**~~ ✅ | 가중치 `rules.yaml` SSOT화 | `RuleCatalog.ScorePolicy`·`effectiveWeights()` + 두 러너 하드코딩 제거 + `RuleCatalogScoringTest` | 없음 · 현재 채점 결과 **무변경**(회귀 테스트로 고정) |
| ~~**P0-b**~~ ✅ | 드라이버 검증 게이트 | `scripts/review-verify.sh` | 낮음 · 전체 컴파일이라 단일파일 javac보다 강함 |
| ~~**P1**~~ ✅ | `DRIVER.md` 절차에 5·7번 편입 | `DRIVER.md` 0~9단계 재작성 + 역할표에 '검증' 행 | 없음 |
| ~~**P1**~~ ✅ | 교훈 수집 지점 이동(§5) | `DRIVER.md` 8번 = 승인+교훈 동시(사전 항목별 질문 삭제) | 낮음 |
| ~~**P2**~~ ✅ | 판정 일원화 — `AutoLoopOrchestrator` 판정 역할 흡수 | `ReviewLoopRunner.evidenceFor()` + 테스트 3케이스 · `AutoLoopOrchestrator.run(targets)` (dryRun 제거) · `--dry-run` 제거 | 중 · 테스트 동반 |
| ~~**P2**~~ ✅ | `review-autoloop.sh` → `review-session.sh` 축소 | worktree+브랜치+변경목록만. `reviewAutoFix` 호출·자동커밋·`--push`·`test` 제거 | 낮음 |
| ~~**P3**~~ ✅ | 자율 계열 휴면 처리 · `reviewAutoFix` 문서에서 내림 | 5개 클래스 휴면 javadoc · 태스크 group `review-loop-dormant` · `AUTOLOOP_DESIGN.md` SUPERSEDED 배너 | 낮음 |

**P0만 해도 실익이 났다** — 채점이 정확해지고, 드라이버가 자기 수정을 검증할 수단이 생겼다.
P2 이후는 정리(clean-up) 성격이었다.

### 이행 중 발견한 초안-현실 차이 (수정 반영)

1. **§4의 `./gradlew reviewVerify --args="--files-from <changed>"`는 존재하지 않는다.**
   P0-b가 gradle 태스크를 **일부러 만들지 않기로** 결정했고(중첩 gradle), 검증은 파일 목록을 받지 않는다
   (전체 컴파일이 목적이므로). → §4를 실제 명령 `bash scripts/review-verify.sh [--with-test]`로 고쳤다.
2. **§3.4 표의 `CompileVerification` "승격"은 §3.1과 모순.** §3.1이 그 클래스를 쓰지 않기로 했으므로
   실제 처분은 **휴면 자율 경로 전용**이다. → 표를 §3.1에 맞췄다.
3. **부모 디렉터리 Evidence는 `ReviewLoopRunner`에 이미 있었다**(P0 이전부터). P2에서 한 일은
   그 규약을 `evidenceFor()`로 뽑아 **왜 repo 루트가 아닌지** 주석으로 못박고 테스트로 고정한 것이다.

---

## 7. 이 설계가 건드리지 않는 것

- **훅의 순수성** — pre-push는 여전히 판정만. 수정·커밋·push 안 함. `claude` 호출 안 함.
- **Critical/미완성 처리** — 여전히 push 차단 + 사람. 루프가 손대지 않음.
- **Minor는 push를 막지 않음** — 요청서만 남기고 통과. 통합 후에도 동일.
- **Gate 1(정적) → Gate 2(LLM) 순서** — 그대로.
- **CI 워크플로** — `gate1-semgrep.yml`·`gate2-judge.yml` 무변경.

---

## 8. 별건 (통합과 무관하나 같이 인지)

- **Gate 1이 현재 로컬에서 무력** — `ArchitectureRulesTest`가 없어 훅이 Gate 1을 스킵한다
  (`.githooks/pre-push`가 존재를 검사해 자동 활성화하도록 이미 준비돼 있음). 컨벤션 담당이 추가하면 즉시 켜짐.
- ~~**"Gate 3" 문서-현실 불일치**~~ ✅ **해결** — 진단이 처음 생각보다 심각했다.
  기존 CI는 **PR 코드를 LLM으로 리뷰하는 잡이 아예 없었다.** `gate2-live-judge`는
  `review-loop/golden/`의 **고정 씨앗 파일**로 어댑터 연동만 확인하는 스모크 테스트였고
  (`GeminiAutoFixLoopLiveTest`가 `golden/perf001/QuizListN1.java.txt`를 읽는다), `gate2-deterministic`은
  **루프 자신의 로직**을 검증한다. 둘 다 PR의 변경 파일을 쳐다보지 않는다.
  → PR 코드에 대한 LLM 판정은 **로컬 pre-push 훅에만** 존재했고, `--no-verify`가 그것을 건너뛰면
  그 코드는 머지까지 **한 번도 LLM 판정을 받지 않았다.**

  **조치**: `gate2-review` 잡 신설(`.github/workflows/gate2-judge.yml`). PR base 대비 변경 `.java`를
  `git diff`로 뽑아 훅과 **동일한** `reviewLoop --files-from ... --gate`를 돌린다.
  - Critical·미완성 → 차단. Minor는 차단하지 않음(로컬 정책과 동일).
  - fork PR(secret 없음) → 러너가 "키 없음 → 생략·통과" exit 0 → 외부 기여 PR을 막지 않음.
  - `--max 20` — 훅 기본값 5는 차단 게이트에서 "상한 초과분이 리뷰 없이 통과"하는 구멍이라 상향.
  - 비결정성 방어는 `RuleCatalog.normalize()`가 담당 — LLM이 뱉은 severity를 카탈로그 값으로 덮어쓰고
    없는 ruleId는 버리므로, **LLM 변덕으로 CRITICAL이 생겨 머지를 막는 일은 구조적으로 불가**하다.

    > ⚠️ **뒤집으면 차단력이 0이다 (2026-08-03 확인).** 위 문장은 안전 속성으로만 서술됐지만,
    > 같은 메커니즘 때문에 **Gate 2는 어떤 코드도 차단할 수 없다.** 확인한 체인:
    > - rules.yaml의 judge 규칙 3개(`CONV_001`·`PERF_001`·`ARCH_003a`)가 **전부 `severity: MINOR`**
    >   → `normalize()`가 severity를 카탈로그 값으로 덮으므로 CRITICAL finding 생성 불가
    >   → `hasCritical` 항상 false → `AWAITING_HUMAN` **도달 불가**
    > - `FindingSource.ACCEPTANCE` finding을 만드는 **프로덕션 코드가 없다**(JudgeScorer가 읽기만 함)
    >   → `acceptanceUnmet` 항상 false → `INCOMPLETE` **도달 불가**
    > - `isBlocking()` = `INCOMPLETE || AWAITING_HUMAN` → **항상 false**
    >
    > 즉 훅·이 문서·`gate2-review`가 약속하는 "Critical/미완성 차단"은 **실행될 수 없는 경로**이고,
    > Gate 2의 실효는 **수정 요청서 생성(리포터)**이다. `PrePushGatePolicyTest`가 `isBlocking()`의
    > *매핑만* 검사해서 초록이었던 것이 이걸 가렸다.
    >
    > **조치(완료)**: `RuleCatalog.blockingRules()` 신설 → 러너가 `--gate` 실행마다 "차단 가능(CRITICAL) N개"와
    > 0개일 때 경고를 출력. `PrePushGatePolicyTest`에 **실제 rules.yaml 기준 도달 가능성** 테스트 추가
    > (CRITICAL 규칙이 생기면 실패하며 문서 갱신을 요구). `FindingSource.ACCEPTANCE`에 미배선 명시.
    >
    > **남은 결정(팀)**: 진짜 차단이 필요한가. 필요하면 `rules.yaml`에 `severity: CRITICAL` 규칙을 추가하면
    > 되고(P0-a 이후 가중치는 yaml SSOT라 코드 수정 불필요), 필요 없으면 "Gate 2 = 리포터"로 확정하고
    > 차단은 Gate 1·semgrep 몫으로 둔다. **무엇이 머지를 막아야 하는지는 코드가 정할 문제가 아니다.**
  - 기존 스모크 테스트는 informational로 유지(어댑터·프롬프트 회귀 감시엔 여전히 유효).

  **남은 전제**: 이 차단이 강제되려면 GitHub **브랜치 보호 "Require status checks to pass"**가 켜져 있어야 한다.
  저장소 설정이라 코드에서 확인 불가 — 사람이 Settings → Branches에서 확인할 것.
- **P4 교훈 자동 기록** — 커밋이 revert되거나 CI 실패 시 `FALSE_POSITIVE` 자동 기록(미구현).
  §5로 수집 지점이 좋아지므로 우선순위는 내려간다.
