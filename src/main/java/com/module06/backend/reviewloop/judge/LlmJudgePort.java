package com.module06.backend.reviewloop.judge;

import java.util.List;

/**
 * LLM Judge의 경계(seam). 코드/diff + 의미규칙(정책) → findings(점수 아님).
 *
 * 실제 구현(ClaudeJudgeAdapter, claude-opus-4-8 + structured outputs)은 API 키가 필요해
 * 다음 스텝에서 붙인다. 이 인터페이스 덕분에 점수 산출·Evidence 검증 등 결정론 로직은
 * LLM 없이(테스트 더블로) 검증된다.
 */
public interface LlmJudgePort {

    /**
     * @param filePath 대상 파일 경로
     * @param code     대상 코드(또는 diff)
     * @param policy   rules.yaml에서 뽑은 의미규칙(Judge가 맡는 것만) 프롬프트
     * @return LLM이 찾은 findings (점수 없음)
     */
    List<Finding> review(String filePath, String code, String policy);
}
