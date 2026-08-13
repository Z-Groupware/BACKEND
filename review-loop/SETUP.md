# 리뷰 루프 · 팀원 세팅 가이드

클론 후 **한 번만** 하면 된다. 순서대로 5분.
절차(무엇을 어떻게 쓰는지)는 [DRIVER.md](DRIVER.md), 설계 배경은 [UNIFIED_DESIGN.md](UNIFIED_DESIGN.md).

---

## 0. 무엇이 켜지는지 먼저 알고 시작

| 게이트 | 무엇을 보나 | push 차단 | 키 필요 |
|---|---|---|---|
| **Gate 1** · ArchUnit | 레이어 규칙(ARCH_001~003) | ✅ **차단** | ❌ |
| **Gate 2** · LLM Judge | 변경 `.java`의 의미 규칙(N+1·유틸 재발명·도메인 침범) | ❌ **리포터** | ✅ `GEMINI_API_KEY` |
| CI · semgrep | 신규 `@Query`(QUERY_002) · 회사 경계 없는 파생 쿼리(TENANT_001) · `@PreAuthorize` 없는 엔드포인트(AUTHZ_001) | ✅ 차단 | ❌ |

- **Gate 2는 push를 막지 않는다.** 수정 요청서만 남긴다 — 정책이다(DRIVER.md 참조).
- 키가 없으면 Gate 2는 **조용히 생략하고 통과**시킨다. 즉 키 없이도 push는 된다(리뷰만 못 받는다).
- 지금 Gate 1은 대상 클래스가 0개다(레이어 패키지 미생성) → 통과. 레이어를 만드는 순간부터 강제된다.

---

## 1. 훅 활성화 (필수 · 1회)

```bash
./gradlew installReviewHooks
```

`git config core.hooksPath .githooks`를 설정한다. 이걸 안 하면 **push할 때 아무 게이트도 돌지 않는다.**
확인:

```bash
git config core.hooksPath
```

`.githooks`가 나와야 한다.

## 2. GEMINI_API_KEY 등록 (Gate 2를 쓸 사람만)

발급: [Google AI Studio](https://aistudio.google.com/apikey) → API 키 생성.

**Windows (PowerShell)** — 값에 개행이 섞이지 않게 `.Trim()`을 꼭 통과시킨다:

```powershell
[Environment]::SetEnvironmentVariable('GEMINI_API_KEY', '<발급받은 키>'.Trim(), 'User')
```

**macOS · Linux** — `~/.zshrc` 또는 `~/.bashrc`에:

```bash
export GEMINI_API_KEY="<발급받은 키>"
```

> ⚠️ **개행 주의.** 키 끝에 개행이 붙으면 예전에는 판정이 매번 크래시했다(JDK가 HTTP 헤더의 개행을 거부).
> 지금은 코드가 `ApiKeys.require()`로 정규화해 방어하지만, 그 변수를 읽는 다른 도구는 여전히 깨진다.
> `echo $GEMINI_API_KEY > file` 같은 방식으로 값을 옮기지 말 것.

등록 후 **터미널·IDE를 새로 열어야** 적용된다. 확인(값을 출력하지 말고 길이만):

```bash
printf '%s' "$GEMINI_API_KEY" | wc -c
```

## 3. 로컬 DB 설정

템플릿을 **프로젝트 루트**에 복사해서 값을 채운다:

```bash
cp application-secret.yml.example application-secret.yml
```

- **`src/main/resources` 아래에 두지 말 것.** 거기 두면 `.gitignore`와 무관하게 `processResources`가 jar에
  담고, `Dockerfile`의 `COPY src`로 **이미지에도 들어간다**(비밀값 유출). 규약은 루트다.
- 키 이름은 운영과 같다. 운영은 이 파일 없이 SSM Parameter Store가 같은 이름의 환경변수를 주입하고
  (`infra/deploy.sh`), **환경변수가 이 파일보다 우선한다.**
- 필요한 키는 `application.yaml`의 `${...}` 자리를 보면 항상 확인할 수 있다(그게 유일한 진실).
  현재: `DB_HOST` · `DB_NAME` · `DB_USERNAME` · `DB_PASSWORD` · `JPA_DDL_AUTO` · `FLYWAY_OUT_OF_ORDER`.

> ⚠️ **`DB_URL`이라는 키는 없다.** `application.yaml`이 url을 직접 조립한다:
> `jdbc:mysql://${DB_HOST}:3306/${DB_NAME}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8`
> `DB_URL`만 넣으면 `${DB_HOST}`가 미해결로 남아 부팅이 실패한다(`Could not resolve placeholder 'DB_HOST'`).
> 템플릿에 `DB_URL`만 있던 것을 이 브랜치에서 `DB_HOST`·`DB_NAME`으로 고쳤다.

## 4. 동작 확인

```bash
bash scripts/review-verify.sh --with-test
```

컴파일 + 전체 테스트. 통과하면 Gate 1(ArchUnit)도 함께 초록이라는 뜻이다.

Gate 2까지 확인하려면(키 필요 · Gemini 호출 1회):

```bash
./gradlew reviewLoop --args="--path src/main/java/com/module06/backend/reviewloop/judge --max 1"
```

출력에 `[OK ] ... → score 100 · PASS`가 보이면 정상이다. 함께 나오는 두 줄도 정상 상태다:
- `역할 : 리포터 — 차단 규칙 0개` → Gate 2 정책(차단 안 함)
- `교훈 : 0건 — 학습 루프 미가동` → 아무도 아직 판정을 기록하지 않았다는 뜻(§아래)

---

## 이제 어떻게 쓰나

**대부분은 아무것도 안 해도 된다.** `git push`하면 훅이 알아서 돈다.

```
git push
 ├─ Gate 1 실패        → push 중단. 아키텍처 위반이니 고쳐야 한다.
 ├─ Gate 2 게이트 오류 → push 중단. ⚠️ 코드 문제가 아니라 '리뷰가 안 돌았다'는 뜻
 │                       (키 형식·네트워크·컴파일). 원인 고치고 재시도.
 ├─ Minor 발견         → push는 통과 + 수정 요청서 생성
 └─ 통과              → push 진행
```

요청서(`.git/review-fix-request.md`)가 생기면 Claude Code에게 그대로 넘긴다:

> `.git/review-fix-request.md` 처리해

Claude Code가 수정 → `review-verify.sh` 검증 → 재판정 → diff를 항목별로 보여주고 승인을 받는다.
상세 절차·예산(AutoFix ≤3 · Total ≤6)은 [DRIVER.md](DRIVER.md).

### 교훈 기록 — 이걸 안 하면 루프가 학습하지 않는다

diff를 승인하거나 되돌릴 때 한 줄 기록한다. **요청서에 복붙용 명령이 규칙 ID까지 채워져 들어 있다.**

```bash
./gradlew reviewLesson --args="--rule PERF_001 --kind CONFIRMED --note '무엇을 고쳤는지'"       # 수락
./gradlew reviewLesson --args="--rule PERF_001 --kind FALSE_POSITIVE --note '왜 오탐인지'"      # 되돌림
./gradlew reviewAccuracy                                                                        # 규칙별 오탐률
```

**왜 이걸 해야 하나** — 루프가 교훈을 쓰는 쪽과 읽는 쪽이 이렇게 나뉘어 있다:

| 방향 | 누가 하나 | 무슨 일 |
|---|---|---|
| **읽기** | 코드가 자동 | 판정할 때마다 `lessons.jsonl`을 읽어 Gemini 프롬프트에 붙인다(`ReviewLoopRunner` → `JudgePromptBuilder`). 아무도 손대지 않아도 매번 일어난다. |
| **쓰기** | **사람이 직접** | `reviewLesson` 명령을 쳐야 `lessons.jsonl`에 한 줄 쌓인다. 자동으로 쌓이는 경로는 revert 회수(아래) 하나뿐이다. |

즉 **프롬프트에 넣어주는 관은 이미 깔려 있는데, 그 관에 부어줄 물이 없으면 아무 일도 안 일어난다.**
실제로 이 저장소의 `lessons.jsonl`은 오랫동안 0건이었고, 그동안 Judge는 매번 "빈 교훈 목록"을 받아
같은 오탐을 반복할 수밖에 없었다. 기록을 빼먹으면 판정 품질이 영원히 제자리다.

---

## 문제가 생기면

| 증상 | 원인 · 조치 |
|---|---|
| push할 때 아무 메시지도 없다 | 훅 미활성화 → `./gradlew installReviewHooks` |
| `Gate 1 · ArchUnit 테스트 없음 → 스킵` | `ArchitectureRulesTest`가 없는 상태. 있으면 자동 활성화된다 |
| `GEMINI_API_KEY 없음 → Gate 2 생략` | 키 미등록(정상 동작). Gate 2를 쓰려면 §2 |
| `Gate 2 게이트 오류` | **코드 문제가 아니다.** 리뷰가 수행되지 않았다 — 키 형식·네트워크·쿼터 확인 |
| `Gate 2 차단 결정(Critical/미완성)` | 코드 판정. 현 정책에선 이 경로에 도달하지 않으므로, 보이면 정책이 바뀐 것이다 |
| 같은 Minor가 계속 올라온다 | 오탐이면 `--kind FALSE_POSITIVE`로 기록할 것. 그래야 다음 판정에서 빠진다 |
| 급하다 | `git push --no-verify` — **로컬 훅만** 건너뛴다. Gate 1·semgrep은 PR에서 다시 잡는다 |

`--no-verify`를 쓰면 그 변경은 **LLM 판정을 한 번도 받지 않고** 나간다. 습관이 되면 루프가 죽는다.

---

## 참고 · 명령 요약

| 명령 | 용도 |
|---|---|
| `./gradlew installReviewHooks` | 훅 활성화(1회) |
| `bash scripts/review-verify.sh [--with-test]` | 수정 검증(컴파일 / 커밋 직전 테스트) |
| `./gradlew reviewLoop --args="--path <dir> --max N"` | 특정 경로 수동 판정 |
| `bash scripts/review-fix.sh --path <dir>` | 판정 + 수정 요청서 생성 한 번에 |
| `./gradlew reviewLesson` / `reviewAccuracy` | 교훈 기록 / 오탐률 조회 |
| `./gradlew reviewOptimize` | **루프 자신의 지표**(커버리지·수율·학습 전환율) + 조치 — 주기 점검 |
| `./gradlew reviewBudget --args="--reset"` | 새 작업 시작 시 예산 초기화 |
| `bash scripts/review-session.sh` | (선택) 격리 worktree 준비 — 변경이 클 때 |
| `bash scripts/review-trail.sh <findings>` | 커밋 직후 수정 이력 기록(revert 자동 회수의 전제) |
| `bash scripts/review-lesson-from-revert.sh` | revert된 수정 → 오탐 후보 보고(기록은 `--apply` + 확인 트레일러) |

`./gradlew tasks --group review-loop`로 전체 목록을 볼 수 있다.
`review-loop-dormant` 그룹은 휴면(무인 자율 수정) — 기본 경로가 아니다.
