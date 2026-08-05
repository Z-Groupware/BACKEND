package com.module06.backend.reviewloop.judge;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 라이브 테스트에서 <b>"제공자를 쓸 수 없는 상태"</b>와 <b>"판정 결과가 틀린 것"</b>을 가른다.
 *
 * <p><b>왜 필요한가(실제 사고 기록)</b>: {@code GEMINI_API_KEY}가 <i>주입돼 있으면서</i> 그 키로
 * 호출이 거부되면, {@code @EnabledIfEnvironmentVariable} 가드는 통과하고(키가 있으므로) 어댑터가
 * {@code Gemini API 오류 <status>}로 던져 <b>테스트 실패</b>가 된다. 그래서 2026-08-05 시점
 * 모든 PR에 {@code gate2-live-judge} 빨간불이 상시 하나 떠 있었다 — 키·쿼터 문제가 코드 판정처럼
 * 보이는 상태다.
 *
 * <p>상시 빨간불의 실제 비용은 "신호 소실"이다. 매번 빨간불이면 사람이 "또 그거겠지"로 학습하고,
 * 그때부터 진짜 실패도 같이 묻힌다(같은 실패 모드를 {@code .githooks/pre-push}도 경고한다).
 *
 * <p>그래서 <b>환경 문제는 건너뛰기(skipped)로 강등</b>하고, 제공자가 정상 응답한 뒤의 불일치만
 * 실패로 남긴다. 판정 회귀 감시라는 이 잡의 목적은 후자에만 있다.
 *
 * <table>
 *   <caption>분류</caption>
 *   <tr><th>상황</th><th>처리</th><th>이유</th></tr>
 *   <tr><td>401 · 403</td><td>건너뜀</td><td>키가 만료·무효 — 코드로 고칠 수 없다</td></tr>
 *   <tr><td>429</td><td>건너뜀</td><td>쿼터 초과 — 시간이 지나면 풀린다</td></tr>
 *   <tr><td>5xx</td><td>건너뜀</td><td>제공자 장애</td></tr>
 *   <tr><td>연결 실패(IOException)</td><td>건너뜀</td><td>러너 네트워크</td></tr>
 *   <tr><td><b>400 · 404</b></td><td><b>실패 유지</b></td><td>요청 형식·모델 id 문제 = 우리 설정 버그</td></tr>
 *   <tr><td>2xx 응답 후 단정 실패</td><td><b>실패 유지</b></td><td>이게 이 잡이 지켜보려던 회귀다</td></tr>
 * </table>
 *
 * <p>건너뛰기 사유에는 <b>HTTP 상태코드만</b> 담고 응답 본문은 담지 않는다. 원인 판별에는 상태코드가
 * 충분하고, 본문을 사유로 실어 나르면 나중에 어떤 값이 섞여 들어올지 보증할 수 없다.
 * (자세한 내용은 실패 시 업로드되는 테스트 리포트에 남는다 — 키는 {@code x-goog-api-key} 헤더로만
 * 가므로 본문에 섞이지 않는다.)
 */
final class SkipOnProviderUnavailable implements TestExecutionExceptionHandler {

    /** {@code GeminiJudgeAdapter} 등이 던지는 메시지 형식 — "Gemini API 오류 403: {...}" */
    private static final Pattern API_ERROR = Pattern.compile("Gemini API 오류 (\\d{3})");

    /** 원인 사슬 탐색 깊이 상한 — 자기참조 cause 로 무한 루프에 빠지지 않게. */
    private static final int MAX_CAUSE_DEPTH = 10;

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        String reason = unavailableReason(throwable);
        if (reason != null) {
            Assumptions.abort(reason);   // TestAbortedException → 실패가 아니라 건너뜀으로 집계된다
        }
        throw throwable;
    }

    /**
     * 건너뛸 사유를 만든다. 건너뛸 상황이 아니면 {@code null}.
     *
     * @param throwable 테스트가 던진 것
     */
    static String unavailableReason(Throwable throwable) {
        Throwable cause = throwable;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++, cause = cause.getCause()) {
            if (cause instanceof IOException) {
                return "제공자에 연결할 수 없어 라이브 판정을 건너뛴다 (" + cause.getClass().getSimpleName()
                        + "). 러너 네트워크·제공자 상태를 확인할 것.";
            }
            int status = statusOf(cause.getMessage());
            if (isUnavailable(status)) {
                return "Gemini 를 쓸 수 없어 라이브 판정을 건너뛴다 (HTTP " + status
                        + " — 키 만료·무효, 쿼터 초과, 제공자 장애 중 하나). 응답 본문은 사유에 담지 않는다 —"
                        + " 자세한 내용은 테스트 리포트 아티팩트를 볼 것.";
            }
        }
        return null;
    }

    /** 메시지에서 HTTP 상태코드를 뽑는다. 형식이 아니면 -1. */
    static int statusOf(String message) {
        if (message == null) {
            return -1;
        }
        Matcher matcher = API_ERROR.matcher(message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    /**
     * 코드로 고칠 수 없는 상태인가.
     *
     * <p>400·404 는 일부러 제외한다 — 잘못된 요청 형식이나 없는 모델 id 는 우리 설정 문제이고,
     * 그건 빨간불로 남아야 고쳐진다.
     */
    static boolean isUnavailable(int status) {
        return status == 401 || status == 403 || status == 429 || (status >= 500 && status < 600);
    }
}
