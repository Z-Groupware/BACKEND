package com.module06.backend.reviewloop.judge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 사람 검토 게이트의 CLI 표면(개발자 도구) — 프론트 없이 터미널에서 findings·AI초안·승인을 보여준다.
 * 실행: ./gradlew reviewCli   (GEMINI_API_KEY 있으면 실제 AI 초안, 없으면 오프라인 초안)
 * 데모라 승인은 자동으로 처리하고 흐름을 출력한다(실제 CLI면 여기서 y/n 입력을 받음).
 */
public final class ReviewLoopCli {

    public static void main(String[] args) throws Exception {
        Path logsDir = Path.of("review-loop/logs");
        Files.createDirectories(logsDir);
        Path lessonsFile = logsDir.resolve("lessons.jsonl");
        KnowledgeStore store = new KnowledgeStore(lessonsFile);
        LessonApprovalService approval = new LessonApprovalService(store);

        // 이 라운드 findings (예시: 자동수정 대상 N+1 + 사람이 오판 의심하는 ARCH_003a)
        Finding perf = new Finding("PERF_001", Severity.MINOR, "performance",
                "quizzes 루프 안에서 제출을 개별 조회 → N+1", "QuizListN1.java", 11, Confidence.HIGH, FindingSource.JUDGE);
        Finding arch = new Finding("ARCH_003a", Severity.MINOR, "architecture",
                "타 도메인 JpaEntity(CourseReferenceEntity)를 조회 목적으로 참조", "GetMyQuizzes.java", 42, Confidence.MEDIUM, FindingSource.JUDGE);

        // 반복 패턴 자동 감지 (최근 이력 예시)
        List<Finding> recentHistory = List.of(arch, arch, arch, arch, perf, perf);
        List<RepeatedPatternDetector.Alert> alerts = new RepeatedPatternDetector(3).detect(recentHistory);

        // 사람이 arch를 오판(FALSE_POSITIVE)으로 지목 → AI가 교훈 노트 초안 생성
        LessonDraftPort drafter = hasGeminiKey()
                ? new GeminiLessonDraftAdapter()
                : (f, kind) -> new Lesson("(draft)", f.ruleId(), kind,
                        "(오프라인 초안) 조회 목적 참조 엔티티는 예외일 수 있음 — 확인 필요");
        Lesson draft = drafter.draft(arch, LessonKind.FALSE_POSITIVE);

        // 데모: 승인 처리 (실제 CLI면 사용자 입력)
        approval.approve(draft, null, "2026-07-15T16:40:00");

        StringBuilder s = new StringBuilder();
        String bar = "==================================================";
        s.append(bar).append('\n');
        s.append("  AI 코드 리뷰 루프 — 사람 검토 게이트 (CLI)\n");
        s.append(bar).append("\n\n");
        for (RepeatedPatternDetector.Alert a : alerts) {
            s.append("[자동 감지] ! ").append(a.ruleId()).append(" 최근 ").append(a.count())
             .append("회 반복 — 정책 불명확 의심 (정책 리뷰 권장)\n");
        }
        s.append("\n리뷰 대상: QuizListN1.java\n");
        s.append("verdict  : score 75 · NEEDS_REVISION · findings 2\n\n");

        s.append("- finding #1 ------------------------------\n");
        s.append("  rule : ").append(perf.ruleId()).append(" (").append(perf.severity()).append(")\n");
        s.append("  위치 : ").append(perf.file()).append(':').append(perf.line()).append('\n');
        s.append("  설명 : ").append(perf.description()).append('\n');
        s.append("  => 자동수정 대상 (Minor)\n\n");

        s.append("- finding #2 ------------------------------\n");
        s.append("  rule : ").append(arch.ruleId()).append(" (").append(arch.severity()).append(")\n");
        s.append("  위치 : ").append(arch.file()).append(':').append(arch.line()).append('\n');
        s.append("  설명 : ").append(arch.description()).append('\n');
        s.append("  사람 판단 : FALSE_POSITIVE (오판 의심)\n\n");
        s.append("  [AI 초안 교훈]\n");
        s.append("    ").append(draft.humanNote()).append('\n');
        s.append("    [ 승인 ]  [ 수정 후 승인 ]  [ 반려 ]\n");
        s.append("    > 승인 선택 (데모)\n");
        s.append("    OK lessons.jsonl 저장됨\n\n");

        s.append("- 축적된 교훈 (lessons.jsonl) --------------\n");
        int i = 1;
        for (Lesson l : store.lessons()) {
            s.append("  ").append(i++).append(". [").append(l.kind()).append("] ")
             .append(l.ruleId()).append(" — ").append(l.humanNote()).append('\n');
        }
        s.append(bar).append('\n');

        String screen = s.toString();
        System.out.println(screen);                                  // 터미널 출력
        Files.writeString(Path.of("build/review-cli-screen.txt"), screen);  // 깨끗한 확인용
    }

    private static boolean hasGeminiKey() {
        String k = System.getenv("GEMINI_API_KEY");
        return k != null && !k.isBlank();
    }

    private ReviewLoopCli() {
    }
}
