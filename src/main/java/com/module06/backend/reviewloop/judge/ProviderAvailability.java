package com.module06.backend.reviewloop.judge;

import javax.net.ssl.SSLException;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "제공자를 쓸 수 없는 상태"와 "코드 판정"을 가른다 — 단일 진실 공급원.
 * {@link ApiKeys}·{@link GeminiModels}와 같은 자리다.
 *
 * <p><b>왜 필요한가(실제 사고 기록)</b>: 2026-08-05, Gemini 선불 크레딧이 소진되어 모든 호출이
 * {@code 429 RESOURCE_EXHAUSTED}로 거부됐다. 그 결과 두 곳이 동시에 죽었다.
 * <ul>
 *   <li>CI {@code gate2-live-judge} — 모든 PR에 빨간불이 상시 하나. 키·쿼터 문제가 <b>코드 판정처럼</b> 보였다</li>
 *   <li>로컬 {@code pre-push} 훅 — 판정을 받지 못해 ERROR로 <b>모든 .java push가 차단</b>됐다</li>
 * </ul>
 *
 * <p>둘 다 같은 오분류다. 제공자를 못 쓰는 것은 <b>이 저장소 코드의 문제가 아니다.</b>
 * 이미 {@code GEMINI_API_KEY} 부재는 "판정 생략·통과"로 처리하는 정책이 있고
 * ({@link ReviewLoopRunner} 참조), 쿼터 소진·인증 거부·제공자 장애도 같은 성질이다.
 *
 * <p><b>이 분류가 게이트를 느슨하게 만드는 것이 아니다.</b> 대안은 사람들이 {@code --no-verify}로
 * 우회하는 것이고, 그러면 결정론 게이트(Gate 1 ArchUnit)까지 같이 꺼진다. 환경 문제를 통과로
 * 처리하면 Gate 1은 계속 돈다.
 *
 * <table>
 *   <caption>분류</caption>
 *   <tr><th>상황</th><th>처리</th><th>이유</th></tr>
 *   <tr><td>401 · 403</td><td>사용 불가</td><td>키 만료·무효 — 코드로 고칠 수 없다</td></tr>
 *   <tr><td>429</td><td>사용 불가</td><td>쿼터·크레딧 소진 — 결제·시간의 문제다</td></tr>
 *   <tr><td>5xx</td><td>사용 불가</td><td>제공자 장애</td></tr>
 *   <tr><td>연결 실패(아래 목록)</td><td>사용 불가</td><td>네트워크</td></tr>
 *   <tr><td><b>400 · 404</b></td><td><b>코드·설정 문제</b></td><td>요청 형식·모델 id — 빨간불로 남아야 고쳐진다</td></tr>
 *   <tr><td><b>그 밖의 {@code IOException}</b></td><td><b>코드·환경 문제</b></td><td>파일 읽기·로그 기록 실패 — 아래 참조</td></tr>
 *   <tr><td>2xx 응답 후의 실패</td><td><b>코드 판정</b></td><td>이게 게이트가 지켜보려던 것이다</td></tr>
 * </table>
 *
 * <p><b>{@code IOException} 전체를 네트워크로 보지 않는다.</b> {@link ReviewLoopRunner}는 판정 도중
 * {@code Files.readString}(대상 파일 읽기)과 감사 로그 기록에서도 {@code IOException}을 던진다.
 * 그것까지 "제공자 사용 불가"로 묶으면 <b>디스크 오류가 게이트 통과가 된다</b> — 이 클래스가
 * 막으려던 실패("판정을 못 했는데 초록불")를 정확히 반대 방향으로 재현하는 셈이다.
 * Jackson의 직렬화 실패({@code JsonProcessingException})도 {@code IOException}이라 같은 구멍에 들어간다.
 * 그래서 <b>전송 계층에서만 나올 수 있는 예외</b>로 좁힌다(CodeRabbit PR #63 지적).
 *
 * <p>사유 문자열에는 <b>HTTP 상태코드만</b> 담고 응답 본문은 담지 않는다. 본문을 사유로 실어 나르면
 * 나중에 어떤 값이 섞여 들어올지 보증할 수 없다. 본문이 필요한 진단은 호출자가 원본 예외를 따로 찍는다.
 */
final class ProviderAvailability {

    /** {@link GeminiJudgeAdapter} 등이 던지는 메시지 형식 — "Gemini API 오류 429: {...}" */
    private static final Pattern API_ERROR = Pattern.compile("Gemini API 오류 (\\d{3})");

    /** 원인 사슬 탐색 깊이 상한 — 자기참조 cause 로 무한 루프에 빠지지 않게. */
    private static final int MAX_CAUSE_DEPTH = 10;

    private ProviderAvailability() {
    }

    /**
     * 제공자를 쓸 수 없는 상태인가 — 그렇다면 사람이 읽을 사유, 아니면 {@code null}.
     *
     * @param throwable 판정 도중 던져진 것(원인 사슬 포함)
     */
    static String unavailableReason(Throwable throwable) {
        Throwable cause = throwable;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++, cause = cause.getCause()) {
            if (isConnectionFailure(cause)) {
                return "제공자에 연결할 수 없다 (" + cause.getClass().getSimpleName()
                        + "). 네트워크·제공자 상태를 확인할 것.";
            }
            int status = statusOf(cause.getMessage());
            if (isUnavailable(status)) {
                return "제공자를 쓸 수 없다 (HTTP " + status
                        + " — 키 만료·무효, 쿼터·크레딧 소진, 제공자 장애 중 하나).";
            }
        }
        return null;
    }

    /**
     * 전송 계층에서만 나올 수 있는 예외인가 — 즉 <b>우리 디스크·직렬화로는 만들 수 없는</b> 실패인가.
     *
     * <p>여기 넣어도 되는 조건은 하나다: {@code Files.readString}·{@code Files.writeString}·Jackson이
     * 그 타입을 던질 수 없어야 한다. 그것들이 던질 수 있는 타입을 넣는 순간 로컬 오류가 통과로 바뀐다.
     * ({@code NoSuchFileException}·{@code AccessDeniedException}·{@code JsonProcessingException}은
     * 전부 아래 어느 것의 하위 타입도 아니다.)
     */
    static boolean isConnectionFailure(Throwable cause) {
        return cause instanceof SocketException          // ConnectException · NoRouteToHostException 포함
                || cause instanceof SocketTimeoutException
                || cause instanceof UnknownHostException
                || cause instanceof HttpTimeoutException  // HttpConnectTimeoutException 포함
                || cause instanceof SSLException;
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
