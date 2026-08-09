package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 지식 축적 산출물 생성기 — 사람이 "이 파일들이 어떻게 생겼는지" 열어볼 수 있게 예시를 남긴다.
 *
 * <p><b>실제 데이터를 절대 건드리지 않는다.</b> 예시는 전용 폴더(review-loop/logs/demo/)에만 쓴다.
 * 실제 누적 기록은 review-loop/logs/ 바로 아래에 있고(error_log.jsonl · lessons.jsonl),
 * 그건 Learning Loop의 데이터 소스라 덮어쓰면 안 된다.
 *
 * <p>테스트가 아니다 — 단언하는 것이 없다. 예전엔 이 클래스가 @Test였고 실제 로그 경로에 썼기 때문에
 * ./gradlew test 한 번이면 누적된 판정 기록이 조용히 사라졌다. 그래서 (1) 테스트 스위트에서 빼내고
 * (2) 쓰는 위치를 실제 데이터와 분리했다.
 *
 * <p>실행: {@code ./gradlew reviewKnowledgeDemo}
 * <p>생성물: error_log.jsonl(라운드 감사) · lessons.jsonl(사람 교훈) · report.md(산문)
 */
public final class KnowledgeArtifactsGenerator {

    /** 예시 전용 폴더 — 실제 누적 기록(review-loop/logs/*.jsonl)과 섞이지 않게 분리한다. */
    static final Path DEMO_DIR = Path.of("review-loop/logs/demo");

    public static void main(String[] args) throws IOException {
        Files.createDirectories(DEMO_DIR);
        Path errorLog = DEMO_DIR.resolve("error_log.jsonl");
        Path lessonsLog = DEMO_DIR.resolve("lessons.jsonl");

        // 예시 파일만 새로 만든다(실제 데이터는 이 폴더 밖이라 영향 없음).
        Files.deleteIfExists(errorLog);
        Files.deleteIfExists(lessonsLog);

        // 1) 라운드 감사 로그(무슨 일이 있었나) — 자동수정 루프가 남기는 기록
        AuditLogWriter audit = new AuditLogWriter(errorLog);
        audit.append(new AuditRecord("2026-07-15T12:00:00", 1, GeminiModels.PINNED,
                75, false, JudgeDecision.NEEDS_REVISION, 1, false));
        audit.append(new AuditRecord("2026-07-15T12:00:40", 2, GeminiModels.PINNED,
                100, false, JudgeDecision.PASS, 0, false));

        // 2) 사람 교훈(정정) — HITL 학습 데이터
        KnowledgeStore knowledge = new KnowledgeStore(lessonsLog);
        knowledge.record(new Lesson("2026-07-15T12:05:00", "ARCH_003a", LessonKind.FALSE_POSITIVE,
                "read-model 투영은 예외 — flag 금지"));
        knowledge.record(new Lesson("2026-07-15T13:10:00", "PERF_001", LessonKind.MISSED,
                "상위 서비스 루프 내 조회를 놓침 — 호출부까지 함께 볼 것"));

        // 3) 산문 md(사람용 리포트) — error_log + 교훈을 합쳐 렌더
        Files.writeString(DEMO_DIR.resolve("report.md"), ReviewReport.fromFiles(errorLog, lessonsLog));

        System.out.println("[knowledge-demo] 예시 생성 완료 → " + DEMO_DIR.toAbsolutePath());
        System.out.println("[knowledge-demo] 실제 누적 기록(review-loop/logs/*.jsonl)은 건드리지 않았습니다.");
    }

    private KnowledgeArtifactsGenerator() {
    }
}
