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
import java.util.List;

/**
 * OptionPanelPort의 Gemini 구현 — finding에 대해 처리 안건 3개 + 별도 추천을 structured output(JSON)으로 생성한다.
 * 추천은 안건에 박히지 않고 recommendation 필드로 분리 → 화면에서 "안건 나열 + 추천 별도"로 렌더.
 */
public class GeminiOptionPanelAdapter implements OptionPanelPort {

    private static final String DEFAULT_MODEL = GeminiModels.resolve();
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private static final String RESPONSE_SCHEMA = """
            {
              "type": "OBJECT",
              "properties": {
                "options": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "letter": {"type": "STRING"},
                      "action": {"type": "STRING", "enum": ["FALSE_POSITIVE", "AUTO_FIX", "POLICY_REVIEW"]},
                      "title": {"type": "STRING"},
                      "rationale": {"type": "STRING"}
                    },
                    "required": ["letter", "action", "title", "rationale"]
                  }
                },
                "recommendation": {
                  "type": "OBJECT",
                  "properties": {
                    "pick": {"type": "STRING"},
                    "confidence": {"type": "NUMBER"},
                    "reason": {"type": "STRING"}
                  },
                  "required": ["pick", "confidence", "reason"]
                }
              },
              "required": ["options", "recommendation"]
            }
            """;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper =
            new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final String apiKey;
    private final String model;

    public GeminiOptionPanelAdapter() {
        this(System.getenv("GEMINI_API_KEY"), DEFAULT_MODEL);
    }

    public GeminiOptionPanelAdapter(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY 환경변수가 필요합니다.");
        }
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public OptionPanel propose(Finding finding) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT.formatted(model)))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequest(finding)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Gemini API 오류 " + response.statusCode() + ": " + response.body());
            }
            return parsePanel(response.body());
        } catch (IOException e) {
            throw new RuntimeException("Gemini 판단 패널 실패", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini 판단 패널 중단", e);
        }
    }

    private String buildRequest(Finding finding) throws IOException {
        String system = """
                너는 코드 리뷰 판단 보조다. 아래 finding을 어떻게 처리할지 3가지 안건을 한국어로 제시하라:
                A) 오탐(FALSE_POSITIVE) — 위반이 아님, 교훈으로 기록
                B) 진짜 위반(AUTO_FIX) — 자동수정
                C) 정책 불명확(POLICY_REVIEW) — 규칙 문구 수정
                각 안건에 letter(A/B/C)·action·title·rationale(이유)를 채워라.
                그리고 recommendation에 네가 추천하는 안건 letter(pick)·confidence(0~1)·reason을 '별도로' 밝혀라.
                안건은 동등하게 쓰고, 추천은 recommendation에만 담아라. 최종 선택은 사람이 한다.
                """;
        String user = "규칙:" + finding.ruleId() + " (" + finding.severity() + ")"
                + " | 위치:" + finding.file() + ":" + finding.line()
                + " | Judge 설명:" + finding.description();

        ObjectNode root = mapper.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        ObjectNode content = root.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", user);
        ObjectNode gen = root.putObject("generationConfig");
        // 같은 finding엔 같은 방안 — 사람이 고를 선택지가 실행마다 바뀌면 선택 자체를 신뢰할 수 없다.
        gen.put("temperature", 0);
        gen.put("responseMimeType", "application/json");
        gen.set("responseSchema", mapper.readTree(RESPONSE_SCHEMA));
        return mapper.writeValueAsString(root);
    }

    private OptionPanel parsePanel(String responseBody) throws IOException {
        JsonNode parts = mapper.readTree(responseBody)
                .path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return new OptionPanel(List.of(), new Recommendation("", 0, "응답 없음"));
        }
        String json = parts.get(0).path("text").asText("");
        return mapper.readValue(json, OptionPanel.class);
    }
}
