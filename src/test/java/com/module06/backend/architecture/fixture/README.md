의도적으로 ARCH_001~003을 위반하는 픽스처.
ArchitectureRulesTest.RuleActuallyFires가 "규칙이 진짜로 위반을 잡는지" 증명하는 데만 쓴다.
프로덕션 임포터는 DO_NOT_INCLUDE_TESTS라 이 클래스들을 보지 않는다 → 실제 게이트에는 영향 없음.
고치지 말 것: 여기서 위반이 사라지면 그 테스트가 아무것도 증명하지 못한다.
