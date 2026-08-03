package com.module06.backend.reviewloop.judge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 지식 축적 저장소 — 사람 정정(교훈)을 append-only jsonl로 쌓는다(감사 무결성).
 * 쌓인 교훈은 JudgePromptBuilder가 다음 프롬프트에 주입 → HITL 피드백 루프의 기억 장치.
 */
public class KnowledgeStore {

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();

    public KnowledgeStore(Path file) {
        this.file = file;
    }

    /** 사람 검토에서 나온 교훈 1건을 기록(append-only). */
    public void record(Lesson lesson) throws IOException {
        String line = mapper.writeValueAsString(lesson);
        Files.writeString(file, line + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** 축적된 교훈 전부(없으면 빈 리스트). */
    public List<Lesson> lessons() throws IOException {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<Lesson> out = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank()) {
                continue;
            }
            // 손상된 한 줄(강제종료로 인한 부분 쓰기·머지 충돌 마커 등)이 교훈 전체를 못 읽게 만들면 안 된다.
            // lessons.jsonl은 팀이 공유·커밋하는 파일이라 충돌 위험이 특히 높다 → 줄 단위로 건너뛴다.
            try {
                out.add(mapper.readValue(line, Lesson.class));
            } catch (JsonProcessingException e) {
                System.out.println("[knowledge] 손상된 줄 건너뜀(" + file + "): " + e.getOriginalMessage());
            }
        }
        return out;
    }
}
