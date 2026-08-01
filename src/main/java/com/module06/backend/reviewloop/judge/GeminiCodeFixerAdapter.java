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
import java.util.List;
import java.util.stream.Collectors;

/**
 * CodeFixerPort의 Gemini 구현 — findings를 받아 코드를 고친 전체 코드를 반환한다(자동수정 루프의 '고치기' 역할).
 * Judge(찾기)와 분리. Minor만 이 경로로 자동수정되고, Critical은 AutoFixRunner가 애초에 fixer를 안 부른다.
 */
public class GeminiCodeFixerAdapter implements CodeFixerPort {

    private static final String DEFAULT_MODEL = GeminiModels.resolve();
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public GeminiCodeFixerAdapter() {
        this(System.getenv("GEMINI_API_KEY"), DEFAULT_MODEL);
    }

    public GeminiCodeFixerAdapter(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY 환경변수가 필요합니다.");
        }
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String fix(String filePath, String code, List<Finding> findings) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT.formatted(model)))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(90))
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequest(filePath, code, findings)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Gemini API 오류 " + response.statusCode() + ": " + response.body());
            }
            return parseFixedCode(response.body(), code);
        } catch (IOException e) {
            throw new RuntimeException("Gemini 수정 호출 실패", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini 수정 호출 중단", e);
        }
    }

    private String buildRequest(String filePath, String code, List<Finding> findings) throws IOException {
        String issues = findings.stream()
                .map(f -> "- [" + f.ruleId() + "] line " + f.line() + ": " + f.description())
                .collect(Collectors.joining("\n"));
        String system = """
                당신은 코드 수정기다. 아래 지적사항(findings)을 고쳐라.
                고친 파일의 전체 코드만 반환한다 — 설명·주석 해설·마크다운 코드펜스 없이 순수 코드만.
                지적과 무관한 부분은 그대로 둔다.
                지적사항:
                %s
                """.formatted(issues);
        String user = "파일: " + filePath + "\n```\n" + code + "\n```";

        ObjectNode root = mapper.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        ObjectNode content = root.putArray("contents").addObject();
        content.put("role", "user");
        content.putArray("parts").addObject().put("text", user);

        return mapper.writeValueAsString(root);
    }

    private String parseFixedCode(String responseBody, String fallback) throws IOException {
        JsonNode parts = mapper.readTree(responseBody)
                .path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return fallback;
        }
        String text = parts.get(0).path("text").asText("");
        return text.isBlank() ? fallback : stripFences(text);
    }

    /** 모델이 ```java ... ``` 로 감싸면 벗겨낸다. */
    private String stripFences(String s) {
        String t = s.strip();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) {
                t = t.substring(firstNewline + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.strip();
    }
}
