package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.module06.backend.cap.application.port.out.CaptionStreamPort;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.global.exception.BusinessException;

/*
 * CAP-13 자막 실시간 구독 서비스의 회의 존재·참석자 검증, 통과 시 CaptionStreamPort로 위임하는지 검증한다.
 */
@DisplayName("CAP-13 자막 실시간 구독 서비스")
class SubscribeToCaptionsServiceTest {

    /* 회의가 없으면 CAP-002로 거절하고 emitter를 만들지 않는지 검증한다. */
    @Test
    @DisplayName("회의가 없으면 CAP-002로 거절한다")
    void rejectsWhenMeetingMissing() {
        SubscribeToCaptionsService service = service(false, true);

        assertErrorCode(() -> service.subscribeToCaptions(500L, 7L), "CAP-002");
    }

    /* 참석자가 아니면 CAP-010으로 거절하는지 검증한다. */
    @Test
    @DisplayName("참석자가 아니면 CAP-010으로 거절한다")
    void rejectsWhenNotAttendee() {
        SubscribeToCaptionsService service = service(true, false);

        assertErrorCode(() -> service.subscribeToCaptions(500L, 7L), "CAP-010");
    }

    /* 참석자면 CaptionStreamPort에 위임해 그 결과 emitter를 그대로 반환하는지 검증한다. */
    @Test
    @DisplayName("참석자면 CaptionStreamPort가 만든 emitter를 그대로 반환한다")
    void delegatesToStreamPortForAttendee() {
        Long[] capturedMeeting = new Long[1];
        Long[] capturedMember = new Long[1];
        SseEmitter expected = new SseEmitter(0L);
        CaptionStreamPort streamPort = (meetingId, memberId) -> {
            capturedMeeting[0] = meetingId;
            capturedMember[0] = memberId;
            return expected;
        };
        SubscribeToCaptionsService service = new SubscribeToCaptionsService(meetingRef(true, true), streamPort);

        SseEmitter result = service.subscribeToCaptions(500L, 7L);

        assertThat(capturedMeeting[0]).isEqualTo(500L);
        assertThat(capturedMember[0]).isEqualTo(7L);
        assertThat(result).isSameAs(expected);
    }

    private MeetingReferenceRepository meetingRef(boolean exists, boolean attendee) {
        return new MeetingReferenceRepository() {
            @Override
            public boolean existsById(Long meetingId) {
                return exists;
            }

            @Override
            public boolean isAttendee(Long meetingId, Long memberId) {
                return attendee;
            }

            @Override
            public boolean isHost(Long meetingId, Long memberId) {
                return false;
            }

            @Override
            public Optional<Long> findCompanyId(Long meetingId) {
                return Optional.of(1L);
            }

            @Override
            public int countAttendees(Long meetingId) {
                return 0;
            }

            @Override
            public Optional<Long> findProjectId(Long meetingId) {
                return Optional.empty();
            }
        };
    }

    private SubscribeToCaptionsService service(boolean meetingExists, boolean attendee) {
        CaptionStreamPort failingStreamPort = (meetingId, memberId) -> {
            throw new AssertionError("인가를 통과하지 못했는데 스트림 포트가 호출되면 안 됩니다.");
        };
        return new SubscribeToCaptionsService(meetingRef(meetingExists, attendee), failingStreamPort);
    }

    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
