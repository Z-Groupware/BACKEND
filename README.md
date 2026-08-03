# BACKEND
backend server

## 처음 클론했다면

```bash
./gradlew installReviewHooks               # push 게이트 활성화 (필수)
bash scripts/review-verify.sh --with-test  # 컴파일 + 테스트 확인
```

그리고 `src/main/resources/application-secret.yaml`에 로컬 DB 값을 채운다(커밋되지 않음 · 키는 SETUP.md §3).

세팅 전체(키 등록·동작 확인·문제 해결): **[review-loop/SETUP.md](review-loop/SETUP.md)**

## AI 코드 리뷰 루프

| 문서 | 내용 |
|---|---|
| [review-loop/SETUP.md](review-loop/SETUP.md) | 팀원 세팅 — 클론 후 1회 |
| [review-loop/DRIVER.md](review-loop/DRIVER.md) | 절차 — push하면 무슨 일이 일어나고 무엇을 해야 하나 |
| [review-loop/UNIFIED_DESIGN.md](review-loop/UNIFIED_DESIGN.md) | 설계·결정 기록 |
| [review-loop/rules.yaml](review-loop/rules.yaml) | 규칙 카탈로그(SSOT) — 가중치·임계값 포함 |

요약: `git push` → Gate 1(ArchUnit·**차단**) → Gate 2(LLM 판정·**리포터**).
Gate 2는 push를 막지 않고 수정 요청서만 남긴다. 요청서는 Claude Code에게 넘겨 처리한다.
