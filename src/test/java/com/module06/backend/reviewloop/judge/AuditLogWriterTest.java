package com.module06.backend.reviewloop.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** error_log.jsonl append-only — 라운드마다 JSON 한 줄이 덧붙고, 과거 줄은 유지된다. */
class AuditLogWriterTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("두 번 append하면 JSON 2줄, 순서·필드 보존")
    void appendsJsonLines() throws IOException {
        Path logFile = dir.resolve("error_log.jsonl");
        AuditLogWriter writer = new AuditLogWriter(logFile);

        writer.append(new AuditRecord("2026-07-14T00:00:00", 1, "claude-opus-4-8",
                70, false, JudgeDecision.NEEDS_REVISION, 3, false));
        writer.append(new AuditRecord("2026-07-14T00:00:01", 2, "claude-opus-4-8",
                90, false, JudgeDecision.PASS, 1, false));

        List<String> lines = Files.readAllLines(logFile);
        assertThat(lines).hasSize(2);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode first = mapper.readTree(lines.get(0));
        assertThat(first.get("round").asInt()).isEqualTo(1);
        assertThat(first.get("decision").asText()).isEqualTo("NEEDS_REVISION");
        assertThat(first.get("model").asText()).isEqualTo("claude-opus-4-8");

        JsonNode second = mapper.readTree(lines.get(1));
        assertThat(second.get("score").asInt()).isEqualTo(90);
        assertThat(second.get("decision").asText()).isEqualTo("PASS");
    }
}
