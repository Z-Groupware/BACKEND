의도적으로 ARCH_001~003과 도메인 순환을 위반하는 픽스처.
`ArchitectureRulesTest$RuleActuallyFires`가 "규칙이 진짜로 위반을 잡는지" 증명하는 데만 쓴다.

**고치지 말 것** — 여기서 위반이 사라지면 그 테스트가 아무것도 증명하지 못하고,
"초록인데 실은 아무것도 검사하지 않는 규칙"으로 조용히 퇴화한다.

두 게이트가 이 디렉터리를 다르게 다룬다:
- **Gate 1(ArchUnit)** — 프로덕션 임포터가 `DO_NOT_INCLUDE_TESTS`라 애초에 보지 않는다.
- **Gate 2(LLM 판정)** — 파일 목록에서 명시적으로 제외한다(`.githooks/pre-push`,
  `.github/workflows/gate2-judge.yml`). 제외하지 않으면 고칠 수 없는 Minor가 매 push·PR마다
  요청서에 올라오고, 반복되는 무의미한 지적은 사람이 요청서 자체를 무시하게 만든다.
