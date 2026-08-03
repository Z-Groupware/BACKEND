package com.module06.backend.reviewloop.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * LessonDraftPort의 Gemini 구현(반자동) — finding + 사람이 고른 kind로 교훈 노트 초안을 한국어로 제안한다.
 * 노트만 생성(사람이 승인·수정). 최종 결정은 사람. 라이브는 GEMINI_API_KEY 필요.
 */
public class GeminiLessonDraftAdapter implements LessonDraftPort {

    private static final String DEFAULT_MODEL = GeminiModels.resolve();
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public GeminiLessonDraftAdapter() {
        this(System.getenv("GEMINI_API_KEY"), DEFAULT_MODEL);
    }

    public GeminiLessonDraftAdapter(String apiKey, String model) {
        this.apiKey = ApiKeys.require(apiKey, "GEMINI_API_KEY");
        this.model = model;
    }

    @Override
    public Lesson draft(Finding finding, LessonKind kind) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT.formatted(model)))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequest(finding, kind)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Gemini API 오류 " + response.statusCode() + ": " + response.body());
            }
            String note = parseNote(response.body());
            return new Lesson("(draft)", finding.ruleId(), kind, note);
        } catch (IOException e) {
            throw new RuntimeException("Gemini 교훈 초안 실패", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini 교훈 초안 중단", e);
        }
    }

    private String buildRequest(Finding finding, LessonKind kind) throws IOException {
        String system = """
                너는 코드 리뷰 교훈 작성 보조다. 아래 finding이 사람에 의해 '%s'로 판단됐다.
                다음 라운드 Judge가 참고할 한국어 교훈 노트를 딱 한 문장으로 써라.
                노트 문장만 반환한다 — 따옴표·설명·머리말 없이.
                (%s = FALSE_POSITIVE: 위반이 아닌데 잡음 / MISSED: 놓친 위반)
                """.formatted(kind, kind);
        String user = "규칙:" + finding.ruleId() + " | 위치:" + finding.file() + ":" + finding.line()
                + " | Judge 설명:" + finding.description();

        ObjectNode root = mapper.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        ObjectNode content = root.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", user);
        return mapper.writeValueAsString(root);
    }

    private String parseNote(String responseBody) throws IOException {
        JsonNode parts = mapper.readTree(responseBody)
                .path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return "";
        }
        return parts.get(0).path("text").asText("").strip();
    }
}
