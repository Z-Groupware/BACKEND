package com.module06.backend.reviewloop.judge;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * LlmJudgePort의 Gemini 구현 — 팀 Anthropic 키 준비 전, 보유한 Gemini 키로 라이브 데모를 돌리기 위한 대체 provider.
 *
 * 포트가 같으므로 점수·Evidence·루프배선·budget은 그대로 재사용된다(provider만 교체) — 포트 설계의 이점.
 * 아티팩트 §04의 기본 모델은 claude-opus-4-8이며, 이 어댑터는 "provider 교체 가능"을 보여주는 병행 구현이다.
 *
 * 구현: Generative Language REST API + JDK HttpClient + Jackson (별도 SDK 의존성 없음).
 * 키는 GEMINI_API_KEY 환경변수에서 읽어 x-goog-api-key 헤더로 전달한다(코드·URL에 키를 넣지 않는다).
 */
public class GeminiJudgeAdapter implements LlmJudgePort {

    private static final String DEFAULT_MODEL = GeminiModels.resolve();
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    // 응답을 findings JSON으로 강제하는 스키마(Gemini responseSchema — 대문자 타입).
    private static final String RESPONSE_SCHEMA = """
            {
              "type": "OBJECT",
              "properties": {
                "findings": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "ruleId": {"type": "STRING"},
                      "severity": {"type": "STRING", "enum": ["CRITICAL", "MINOR"]},
                      "category": {"type": "STRING"},
                      "description": {"type": "STRING"},
                      "file": {"type": "STRING"},
                      "line": {"type": "INTEGER"},
                      "confidence": {"type": "STRING", "enum": ["LOW", "MEDIUM", "HIGH"]}
                    },
                    "required": ["ruleId", "severity", "description", "file", "line", "confidence"]
                  }
                }
              },
              "required": ["findings"]
            }
            """;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final String apiKey;
    private final String model;

    public GeminiJudgeAdapter() {
        this(System.getenv("GEMINI_API_KEY"), DEFAULT_MODEL);
    }

    public GeminiJudgeAdapter(String apiKey, String model) {
        this.apiKey = ApiKeys.require(apiKey, "GEMINI_API_KEY");   // 개행 제거 — 헤더 값에 개행이 있으면 JDK가 거부
        this.model = model;
    }

    @Override
    public List<Finding> review(String filePath, String code, String policy) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT.formatted(model)))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequest(filePath, code, policy)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Gemini API 오류 " + response.statusCode() + ": " + response.body());
            }
            return parseFindings(response.body());
        } catch (IOException e) {
            throw new RuntimeException("Gemini 호출 실패", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini 호출 중단", e);
        }
    }

    String buildRequest(String filePath, String code, String policy) throws IOException {
        String system = """
                당신은 코드 리뷰 채점자다. 아래 정책(의미규칙)만 기준으로 검토한다.
                점수는 매기지 말고 findings만 반환한다. file/line은 실제 코드에 존재하는 근거여야 한다.
                확신이 낮아도 일단 보고하고 confidence로 표시한다.
                정책:
                %s
                """.formatted(policy);
        String user = "파일: " + filePath + "\n```\n" + code + "\n```";

        ObjectNode root = mapper.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        ObjectNode content = root.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", user);
        ObjectNode genConfig = root.putObject("generationConfig");
        // 판정에 필요한 건 창의성이 아니라 재현성 — 같은 코드는 같은 findings여야 게이트가 성립한다.
        // 미설정 시 모델 기본값(≈1.0)이라 같은 파일에 score가 85↔100으로 흔들렸다.
        genConfig.put("temperature", 0);
        genConfig.put("responseMimeType", "application/json");
        genConfig.set("responseSchema", mapper.readTree(RESPONSE_SCHEMA));

        return mapper.writeValueAsString(root);
    }

    private List<Finding> parseFindings(String responseBody) throws IOException {
        JsonNode candidate = mapper.readTree(responseBody).path("candidates").path(0);

        // 판정이 정상 종료되지 않았는데(잘림·안전차단 등) 빈 findings를 돌려주면
        // "지적 없음 = PASS"가 되어 게이트가 조용히 통과한다 — 게이트에서 가장 위험한 실패 방향이다.
        // 판정 불가는 통과가 아니라 오류로 드러내야 사람이 알아챈다.
        String finishReason = candidate.path("finishReason").asText("");
        if (!finishReason.isEmpty() && !"STOP".equals(finishReason)) {
            throw new IllegalStateException(
                    "Gemini 판정이 정상 종료되지 않음(finishReason=" + finishReason
                    + ") — 판정 불가를 PASS로 처리하지 않는다");
        }

        JsonNode parts = candidate.path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return List.of();
        }
        String json = parts.get(0).path("text").asText("");
        if (json.isBlank()) {
            return List.of();
        }

        JudgeFindingsDto dto = mapper.readValue(json, JudgeFindingsDto.class);
        if (dto.findings() == null) {
            return List.of();
        }
        List<Finding> out = new ArrayList<>();
        for (FindingDto f : dto.findings()) {
            out.add(new Finding(f.ruleId(), parseSeverity(f.severity()), f.category(),
                    f.description(), f.file(), f.line(), parseConfidence(f.confidence())));
        }
        return out;
    }

    private Severity parseSeverity(String raw) {
        try {
            return Severity.valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Severity.MINOR;
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
