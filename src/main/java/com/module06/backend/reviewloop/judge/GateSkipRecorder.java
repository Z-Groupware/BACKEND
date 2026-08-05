package com.module06.backend.reviewloop.judge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Gate 2를 <b>생략하고 통과시킨 사실</b>을 두 곳에 남긴다 — 감사 로그 한 줄 + CI 요약 배너.
 *
 * <p><b>왜 필요한가</b>: {@link ProviderAvailability}가 환경 문제를 통과로 강등하면서
 * 상시 빨간불은 사라졌지만, 그 대가로 <b>초록불이 두 가지 뜻</b>을 갖게 됐다 —
 * "판정을 받고 통과"와 "판정을 아예 못 받음". 구분이 없으면 크레딧이 소진된 며칠 동안
 * 리뷰 없이 나간 변경을 아무도 세지 못한다.
 *
 * <p>이 저장소가 이미 한 번 겪은 실패 모드다: 키 형식 오류로 판정이 매번 크래시했는데
 * <b>감사 로그가 0건인 것을 아무도 몰랐다</b>({@link ReviewLoopRunner} 클래스 주석).
 * 생략을 기록하지 않으면 그때와 같은 상태가 된다 — 이번엔 초록불이라 더 조용하다.
 *
 * <table>
 *   <caption>남기는 곳</caption>
 *   <tr><th>대상</th><th>무엇을 답하나</th></tr>
 *   <tr><td>{@code error_log.jsonl}</td><td>"며칠간 몇 건이 리뷰 없이 나갔나" — 누적 집계</td></tr>
 *   <tr><td>{@code $GITHUB_STEP_SUMMARY}</td><td>"지금 이 PR이 리뷰를 받았나" — 로그를 열지 않아도 보인다</td></tr>
 * </table>
 *
 * <p><b>기록 실패가 게이트를 망가뜨리면 안 된다.</b> 이건 신호이지 게이트가 아니다 —
 * {@link ReviewLoopRunner#writeStatus}와 같은 정책으로 삼키고 경고만 남긴다. 두 기록은
 * 서로 독립적으로 시도한다(감사 로그 쓰기가 실패해도 배너는 올라간다).
 */
final class GateSkipRecorder {

    /** 키 자체가 없는 경우의 사유 — 제공자 응답이 없으므로 상태코드가 없다. */
    static final String NO_API_KEY = "GEMINI_API_KEY 없음 (키 미주입 · fork PR)";

    /** 리뷰되지 않은 파일 수를 셀 수 없는 경우(대상 확정 전에 실패). */
    static final int UNKNOWN_COUNT = -1;

    /** 배너를 JVM당 한 번만 올린다 — 라이브 테스트 5건이 같은 429로 죽으면 배너도 5장이 된다. */
    private static boolean bannerClaimed;

    private GateSkipRecorder() {
    }

    /** 감사 로그 + CI 배너 둘 다. PR 코드를 실제로 판정하는 경로(러너)에서 쓴다. */
    static void record(Clock clock, String reason, int unreviewedFiles) {
        appendAudit(ReviewLoopPaths.AUDIT_LOG, clock, reason, unreviewedFiles);
        warn(reviewBanner(reason, unreviewedFiles));
    }

    /**
     * CI 배너만 — 라이브 스모크 테스트({@code Gemini*LiveTest})용.
     *
     * <p>감사 로그에는 남기지 않는다. 그 잡은 PR 코드를 리뷰하지 않고 <b>연동이 살아있는지</b>만
     * 보므로, 라운드 로그에 섞으면 "리뷰 없이 나간 건수"가 부풀려진다.
     */
    static void warnSmokeSkipped(String reason) {
        warn(smokeBanner(reason));
    }

    /**
     * 스텝 요약에 배너를 올린다. {@code GITHUB_STEP_SUMMARY}가 없으면 아무것도 하지 않는다
     * (로컬 실행 — 러너가 이미 콘솔에 찍는다).
     */
    private static void warn(String markdown) {
        String target = System.getenv("GITHUB_STEP_SUMMARY");
        if (target == null || target.isBlank() || !claimBanner()) {
            return;
        }
        writeBanner(Path.of(target), markdown);
    }

    /** 첫 호출만 true — 같은 원인으로 여러 번 불려도 배너는 한 장이다. */
    private static synchronized boolean claimBanner() {
        if (bannerClaimed) {
            return false;
        }
        bannerClaimed = true;
        return true;
    }

    /** 스텝 요약 파일에 덧붙인다. 다른 스텝이 쓴 요약을 덮지 않도록 APPEND 다. */
    static void writeBanner(Path stepSummary, String markdown) {
        try {
            Files.writeString(stepSummary, markdown, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            System.out.println("[GATE] CI 요약 배너 기록 실패(" + stepSummary + "): " + e);
        }
    }

    /**
     * 감사 로그 한 줄. {@code skipReason}이 채워진 행은 "이 시각에 판정이 수행되지 않았다"는 뜻이고,
     * {@code findingsCount}에는 리뷰되지 않은 .java 수를 담는다 — 집계 대상이 그것이다.
     */
    static void appendAudit(Path auditLog, Clock clock, String reason, int unreviewedFiles) {
        try {
            if (auditLog.getParent() != null) {
                Files.createDirectories(auditLog.getParent());
            }
            new AuditLogWriter(auditLog).append(
                    AuditRecord.skipped(LocalDateTime.now(clock).toString(), reason, unreviewedFiles));
        } catch (IOException | RuntimeException e) {
            System.out.println("[GATE] 감사 로그 기록 실패(" + auditLog + "): " + e + " — 사유는 " + reason);
        }
    }

    /**
     * PR 코드가 리뷰되지 않았음을 알리는 배너. 스텝 요약은 잡 페이지 최상단에 렌더되므로
     * 로그를 펼치지 않아도 보인다 — 그게 이 배너의 존재 이유다(초록불만 보고 넘어가는 것을 막는다).
     */
    static String reviewBanner(String reason, int unreviewedFiles) {
        String count = unreviewedFiles < 0
                ? "(대상 확정 전 실패 — 셀 수 없음)"
                : unreviewedFiles + "개";
        return """
                > [!WARNING]
                > ## Gate 2(LLM 판정) 생략 — 이 변경은 LLM 리뷰를 받지 않았다
                >
                > **사유**: %s
                >
                > **리뷰되지 않은 `.java`**: %s
                >
                > 초록불은 '판정 통과'가 아니라 **'판정 미수행'**이다. 환경 문제(키·쿼터·제공자 장애)를
                > 통과로 처리하는 것은 `--no-verify` 우회를 막기 위한 것이지 리뷰를 면제한 것이 아니다.
                > 제공자를 다시 쓸 수 있게 되면 이 구간의 커밋은 소급 리뷰 대상이다.
                >
                > 결정론 게이트(Gate 1 ArchUnit · semgrep)는 그대로 돌았다.

                """.formatted(reason, count);
    }

    /**
     * 라이브 스모크 테스트를 건너뛴 배너. 리뷰 배너와 문구를 나눈 이유는, 이 잡이 건너뛰어도
     * <b>PR 코드의 리뷰 여부와는 무관</b>하기 때문이다 — 같은 문구를 쓰면 둘을 혼동하게 된다.
     */
    static String smokeBanner(String reason) {
        return """
                > [!WARNING]
                > ## 라이브 Judge 스모크 테스트 건너뜀 — 어댑터·프롬프트 회귀를 확인하지 못했다
                >
                > **사유**: %s
                >
                > 이 잡은 PR 코드를 리뷰하지 않는다(연동이 살아있는지만 본다). 따라서 이 배너는
                > "리뷰를 못 받았다"는 뜻이 아니라 **"회귀 감시가 이번엔 돌지 않았다"**는 뜻이다.
                > 제공자가 복구되기 전까지 어댑터·프롬프트 변경은 이 그물에 걸리지 않는다.

                """.formatted(reason);
    }
}
