package com.module06.backend.reviewloop.judge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;

import java.util.List;

/**
 * LlmJudgePort의 실제 구현 — claude-opus-4-8 + structured outputs로 findings를 생성한다.
 *
 * 점수는 매기지 않는다(LLM은 findings만). 점수·라우팅은 JudgeScorer가, 환각 제거는 EvidenceValidator가 한다.
 * 모델 기본값은 스킬 규약대로 claude-opus-4-8, CI 비용 부담 시 생성자로 sonnet-5 등 명시적 하향.
 *
 * 라이브 호출은 ANTHROPIC_API_KEY가 필요하다(fromEnv). 결정론 로직은 이 어댑터 없이도 테스트된다.
 */
public class ClaudeJudgeAdapter implements LlmJudgePort {

    private static final String DEFAULT_MODEL = "claude-opus-4-8";

    private final AnthropicClient client;
    private final String model;

    /** ANTHROPIC_API_KEY 환경변수(또는 ant 프로파일)로 클라이언트를 구성한다. */
    public ClaudeJudgeAdapter() {
        this(AnthropicOkHttpClient.fromEnv(), DEFAULT_MODEL);
    }

    public ClaudeJudgeAdapter(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public List<Finding> review(String filePath, String code, String policy) {
        String system = """
                당신은 코드 리뷰 채점자다. 아래 정책(의미규칙)만 기준으로 코드를 검토한다.
                점수는 매기지 마라 — findings만 반환한다.
                각 finding의 file/line은 실제 코드에 존재하는 근거여야 한다(없는 위치를 지어내지 마라).
                확신이 낮아도 일단 보고하고 confidence로 표시한다.

                정책:
                %s
                """.formatted(policy);

        String user = "파일: " + filePath + "\n```\n" + code + "\n```";

        // .outputConfig(Class)를 붙이면 빌더가 typed 빌더로 바뀌어 StructuredMessageCreateParams<T>를 반환한다 → var로 받는다.
        var params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                .system(system)
                .addUserMessage(user)
                .outputConfig(JudgeFindingsDto.class)   // JSON 스키마 강제(structured outputs)
                .build();

        JudgeFindingsDto result = client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(typed -> typed.text())
                .findFirst()
                .orElse(new JudgeFindingsDto(List.of()));

        return result.findings().stream().map(this::toFinding).toList();
    }

    private Finding toFinding(FindingDto dto) {
        return new Finding(
                dto.ruleId(),
                parseSeverity(dto.severity()),
                dto.category(),
                dto.description(),
                dto.file(),
                dto.line(),
                parseConfidence(dto.confidence()));
    }

    private Severity parseSeverity(String raw) {
        try {
            return Severity.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Severity.MINOR;   // 알 수 없으면 보수적으로 MINOR (Critical 오판 방지)
        }
    }

    private Confidence parseConfidence(String raw) {
        try {
            return Confidence.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Confidence.LOW;
        }
    }
}
