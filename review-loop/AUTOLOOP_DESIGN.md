# 리뷰 루프 · 자율 복원 설계 (AUTOLOOP)

`DRIVER.md`(사람 드라이버 게이트)와 **공존**하는 자율 수정 루프. 게이트 훅은 그대로 순수 게이트로 두고,
자율 루프는 **명시 트리거 · 별도 worktree/브랜치**에서만 돈다.

## 확정 정책 (결정 A/B/C)
- **A · 별도 태스크**: pre-push 훅은 손대지 않는다. 루프는 `./gradlew reviewAutoFix`(+ 래퍼 스크립트)로만 기동.
- **B · 자동 커밋 · 별도 브랜치**: 수렴 시 `autoloop/<base>-<shortsha>` 브랜치에 자동 커밋. **push/PR은 팀 규칙대로 사람 승인(`--push` 옵션, 기본 off).**
- **C · Minor 한정 유지**: `AWAITING_HUMAN`(Critical)·`INCOMPLETE`(미완성)은 루프가 즉시 멈추고 사람에게. 자율 수정 대상은 `NEEDS_REVISION`(Minor)뿐. (`AutoFixRunner`가 이미 이 규칙을 지킴)

---

## 지금 있는 것 (재사용, 배선만 하면 삶)
| 조각 | 역할 | 손댈 것 |
|---|---|---|
| `AutoFixRunner` | 파일 1개 수렴 루프(fix→디스크→재리뷰→budget) | no-progress 가드 1개 추가(P2) |
| `CodeFixerPort` / `GeminiCodeFixerAdapter` | 고치기 seam + Gemini 구현 | 그대로 |
| `ReviewLoop`(=`RoundReviewer`) | 한 라운드 판정 | 그대로 |
| `ReviewBudget` | 전역 라운드 캡(기본 6) | 그대로 |
| `JudgeDecision` | PASS/NEEDS_REVISION/AWAITING_HUMAN/INCOMPLETE | 그대로 |

**유일한 근본 갭**: `AutoFixRunner`를 `new` 하는 `main`이 없다. 아래 신규 진입점이 그걸 배선한다.

---

## P0 — 배선 (루프가 돌게)

### 신규 진입점 `AutoLoopRunner` (게이트용 `ReviewLoopRunner`와 분리 — 훅 순수성 유지)
```
com.wanted.backend.reviewloop.judge.AutoLoopRunner#main
  --files-from <list>        변경 .java 목록(래퍼가 diff로 생성)
  --domain <X>               도메인 규칙 한정(선택)
  --max-files <N=5>          비용 방어 상한
  --rounds-per-file <N=3>    파일당 수정 라운드 캡 (DRIVER 예산 AutoFix≤3과 동일)
  --global-budget <N=6>      전역 라운드 캡 (Total≤6)
  --dry-run                  디스크/커밋 안 함, 판정·제안만 출력
```
배선:
```
judge  = new GeminiJudgeAdapter()                       // 찾기
fixer  = new VerifiedFixer(new GeminiCodeFixerAdapter(), verify)   // 고치기+검증 (P1)
verify = new GradleVerification(worktreeRoot)           // 컴파일/타깃 테스트 (P1)
budget = new ReviewBudget(globalBudget)
each file → new AutoFixRunner(reviewLoopFor(file), fixer, budget, audit, clock, model, worktreeRoot).run(...)
```

### 신규 오케스트레이터 `AutoLoopOrchestrator`
변경 파일들을 **하나의 `ReviewBudget`을 공유**하며 순회, 파일별 `AutoFixResult` 집계 → 요약 리포트.
Critical/미완성이 하나라도 나오면 그 파일은 즉시 종료(사람 인계 목록에 적재).

### 신규 gradle 태스크
```gradle
tasks.register('reviewAutoFix', JavaExec) {
    group = 'review-loop'
    description = '자율 수정 루프 — 변경 Minor를 fix→검증→재판정 수렴까지(별도 worktree/브랜치 권장)'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'com.wanted.backend.reviewloop.judge.AutoLoopRunner'
    jvmArgs reviewLoopUtf8Args
}
```

### 신규 래퍼 `scripts/review-autoloop.sh` (worktree·브랜치·커밋 — 결정 A/B)
```
base=$(git rev-parse --abbrev-ref HEAD); sha=$(git rev-parse --short HEAD)
wt=../.autoloop-wt; branch=autoloop/$base-$sha
git worktree add -b "$branch" "$wt" HEAD            # 메인 워킹트리 무손상 (격리)
( cd "$wt"
  git diff --name-only --diff-filter=ACMR origin/develop...HEAD | grep '\.java$' > .git/autoloop-changed.txt
  ./gradlew reviewAutoFix --args="--files-from .git/autoloop-changed.txt --rounds-per-file 3 --global-budget 6"
  if ! git diff --quiet; then
     git commit -am "fix(review): 자율 루프 Minor 수정 (base $base)"     # B: 자동 커밋
  fi )
git worktree remove "$wt"                            # 브랜치는 로컬에 남음
echo "→ 브랜치 $branch 생성·커밋 완료. push/PR은 승인 후: git push -u origin $branch"
# --push 플래그를 준 경우에만 push (팀 규칙: 명시 승인)
```

---

## P1 — 안전막 (이거 없이는 켜지 말 것)

### `VerificationPort` (신규 seam) + `GradleVerification` (구현)
```java
interface VerificationPort { VerifyResult verify(Path filePath); }   // compileJava + 해당 도메인 테스트
record VerifyResult(boolean ok, String log) {}
```
Gradle Tooling API 또는 `./gradlew -q compileJava` 프로세스 호출로 구현. worktree 안에서 실행.

### `VerifiedFixer` (신규 데코레이터 — `AutoFixRunner` 내부 무변경)
```java
class VerifiedFixer implements CodeFixerPort {
  String fix(path, code, findings) {
    String proposed = delegate.fix(path, code, findings);
    Files.writeString(path, proposed);
    if (!verify.verify(path).ok()) { Files.writeString(path, code); return code; } // 컴파일·테스트 실패 → 롤백, 무변경 반환
    return proposed;
  }
}
```
→ LLM fixer가 컴파일 안 되는 코드를 뱉어도 **채택되지 않음.** 무변경 반환 시 다음 라운드 판정이 동일 → 아래 no-progress 가드가 빠르게 종료.

### 격리·롤백
worktree 자체가 격리 경계(B). 나쁜 수렴은 브랜치를 버리면 끝(메인 무영향).

---

## P2~P4 (신뢰도 강화 — 플러그 지점만 명시)
- **P2 no-progress/진동 가드** — `AutoFixRunner.run` 루프에 `lastScore` 추적: `NEEDS_REVISION`인데 score가 2라운드 연속 미개선 → break(발산/진동 조기 종료). fix 결과 diff 해시 사이클도 감지.
- **P3 judge≠fixer 독립성** — `AutoLoopRunner`에서 fixer 모델을 judge와 다르게 주입(자기 승인 방지) + PASS 직전 2차 adversarial verify.
- **P4 학습루프 자동 닫기** — 자율 커밋이 사람에게 revert되거나 CI 실패 시 `reviewLesson --kind FALSE_POSITIVE` 자동 기록 → judge 프롬프트 개선.

---

## 3층 루프 최종 형태
```
inner (파일 수렴)  AutoFixRunner + VerifiedFixer         ← P0 배선 + P1 검증
outer (체인지셋)   AutoLoopOrchestrator (공유 budget)     ← P0 신규
meta  (학습)       reviewLesson/reviewAccuracy 자동화     ← P4
```

## 불변식 재확인 (DRIVER.md와 공존)
- 훅은 여전히 순수 게이트 — 자율 루프는 훅 **바깥** 별도 태스크에서만.
- Critical/미완성은 여전히 사람 — 루프가 건드리지 않음(C).
- push/PR은 여전히 사람 승인 — 자율은 **로컬 브랜치 커밋까지**(B).
