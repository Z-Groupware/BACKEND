package com.module06.backend.capture.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.module06.backend.capture.application.port.out.MeetingAccessPort;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 회사 스코프 관문 — 캡처 파이프라인 공개 API 18개가 전부 지나는 자리다.
 *
 * <p>여기가 뚫리면 로그인한 사원이 남의 회사 회의 id 를 넣어 그 회의의 처리 상태·요약·근거
 * 발화를 가져간다. 실제로 CAP-06 이 그렇게 뚫려 있었다(companyId 를 받아놓고 쓰지 않았다).
 */
class MeetingAccessGuardTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;

    @Test
    @DisplayName("그 회사 회의면 통과한다")
    void 자기_회사_회의는_통과한다() {
        MeetingAccessGuard guard = new MeetingAccessGuard((companyId, meetingId) -> true);

        assertThatCode(() -> guard.requireAccessible(COMPANY, MEETING)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 회사 회의면 404 로 막는다 — 403 이면 존재가 새어 나간다")
    void 다른_회사_회의는_404다() {
        MeetingAccessGuard guard = new MeetingAccessGuard((companyId, meetingId) -> false);

        assertThatThrownBy(() -> guard.requireAccessible(COMPANY, MEETING))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .satisfies(code -> {
                    assertThat(code).isEqualTo(CaptureErrorCode.MEETING_NOT_ACCESSIBLE);
                    // 403 이면 "이 회의는 있지만 당신 것이 아니다"가 새어 나가고,
                    // id 를 훑어 남의 회사 회의 개수를 셀 수 있다.
                    assertThat(code.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });
    }

    @Test
    @DisplayName("포트에 넘기는 인자 순서가 뒤바뀌지 않는다")
    void 인자를_순서대로_넘긴다() {
        // (companyId, meetingId) 를 바꿔 넘기면 우연히 통과하는 조합이 생긴다 —
        // 두 값이 모두 long 이라 컴파일러가 잡아주지 않는 자리다.
        MeetingAccessPort port = (companyId, meetingId) -> companyId == COMPANY && meetingId == MEETING;
        MeetingAccessGuard guard = new MeetingAccessGuard(port);

        assertThatCode(() -> guard.requireAccessible(COMPANY, MEETING)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireAccessible(MEETING, COMPANY))
                .isInstanceOf(BusinessException.class);
    }
}
