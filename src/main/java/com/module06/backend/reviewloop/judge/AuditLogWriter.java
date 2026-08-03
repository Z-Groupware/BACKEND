package com.module06.backend.reviewloop.judge;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * error_log.jsonl append-only 기록기 — 라운드마다 AuditRecord를 JSON 한 줄로 덧붙인다.
 * append-only라 과거 기록은 절대 덮어쓰지 않는다(감사 무결성).
 */
public class AuditLogWriter {

    private final Path logFile;
    private final ObjectMapper mapper = new ObjectMapper();

    public AuditLogWriter(Path logFile) {
        this.logFile = logFile;
    }

    public void append(AuditRecord record) throws IOException {
        String line = mapper.writeValueAsString(record);
        Files.writeString(logFile, line + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public Path logFile() {
        return logFile;
    }
}
