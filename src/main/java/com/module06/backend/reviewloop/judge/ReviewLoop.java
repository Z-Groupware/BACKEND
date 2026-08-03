package com.module06.backend.reviewloop.judge;

import java.util.List;

/**
 * Gate 2 한 파일 리뷰의 배선(조립) — 지금까지의 조각을 하나의 흐름으로 잇는다.
 *
 *   rules.yaml(SSOT) → JudgePromptBuilder(정책)
 *                    → LlmJudgePort(LLM findings)
 *                    → EvidenceValidator(환각 제거)
 *                    → JudgeScorer(결정론 점수·라우팅) → JudgeVerdict
 *
 * LlmJudgePort만 갈아끼우면: 테스트=stub, 런타임/Promptfoo=ClaudeJudgeAdapter. 나머지는 그대로.
 * report-only(관찰 모드)로 먼저 붙였다가, 신뢰가 쌓이면 Gate로 승격한다.
 */
public class ReviewLoop implements RoundReviewer {

    private final LlmJudgePort judge;
    private final RuleCatalog catalog;
    private final EvidenceValidator evidenceValidator;
    private final JudgeScorer scorer;
    private final List<Lesson> lessons;   // 사람 정정 교훈 — 프롬프트에 주입해 과거 오판·누락 반복 방지
    private final JudgePromptBuilder promptBuilder = new JudgePromptBuilder();

    /** 교훈 없이(빈 리스트) — 첫 라운드·테스트용. */
    public ReviewLoop(LlmJudgePort judge,
                      RuleCatalog catalog,
                      EvidenceValidator evidenceValidator,
                      JudgeScorer scorer) {
        this(judge, catalog, evidenceValidator, scorer, List.of());
    }

    /** 축적된 교훈을 판정 프롬프트에 반영 — Learning Loop의 읽기 끝. */
    public ReviewLoop(LlmJudgePort judge,
                      RuleCatalog catalog,
                      EvidenceValidator evidenceValidator,
                      JudgeScorer scorer,
                      List<Lesson> lessons) {
        this.judge = judge;
        this.catalog = catalog;
        this.evidenceValidator = evidenceValidator;
        this.scorer = scorer;
        this.lessons = lessons;
    }

    @Override
    public JudgeVerdict review(String filePath, String code) {
        String policy = promptBuilder.buildPolicy(catalog, lessons);  // rules.yaml + 축적 교훈
        List<Finding> raw = judge.review(filePath, code, policy); // LLM은 findings만
        List<Finding> grounded = evidenceValidator.keepGrounded(raw); // 환각 file:line 제거
        List<Finding> normalized = catalog.normalize(grounded);   // ruleId 화이트리스트 + severity 카탈로그 lookup(LLM 변덕 제거)
        return scorer.score(normalized);                          // 점수·라우팅은 결정론
    }
}
