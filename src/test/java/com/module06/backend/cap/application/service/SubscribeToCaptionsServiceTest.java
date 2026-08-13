package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.port.out.CaptionStreamPort;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.global.exception.BusinessException;

/*
 * CAP-13 자막 실시간 구독 서비스의 회의 존재·host 검증, 통과 시 CaptionStreamPort로 위임하는지 검증한다.
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

    /* host가 아니면 CAP-013으로 거절하는지 검증한다. */
    @Test
    @DisplayName("host가 아니면 CAP-013으로 거절한다")
    void rejectsWhenNotHost() {
        SubscribeToCaptionsService service = service(true, false);

        assertErrorCode(() -> service.subscribeToCaptions(500L, 7L), "CAP-013");
    }

    /* host면 CaptionStreamPort에 위임해 그 결과 emitter를 그대로 반환하는지 검증한다. */
    @Test
    @DisplayName("host면 CaptionStreamPort가 만든 emitter를 그대로 반환한다")
    void delegatesToStreamPortForHost() {
        Long[] capturedMeeting = new Long[1];
        Long[] capturedMember = new Long[1];
        SseEmitter expected = new SseEmitter(0L);
        CaptionStreamPort streamPort = (meetingId, memberId) -> {
            capturedMeeting[0] = meetingId;
            capturedMember[0] = memberId;
            return expected;
        };
        MeetingReferenceRepository meetingRef = meetingRef(true, true);
        SubscribeToCaptionsService service = new SubscribeToCaptionsService(meetingRef, accessGuard(meetingRef),
                streamPort);

        SseEmitter result = service.subscribeToCaptions(500L, 7L);

        assertThat(capturedMeeting[0]).isEqualTo(500L);
        assertThat(capturedMember[0]).isEqualTo(7L);
        assertThat(result).isSameAs(expected);
    }

    private MeetingReferenceRepository meetingRef(boolean exists, boolean host) {
        return new MeetingReferenceRepository() {
            @Override
            public boolean existsById(Long meetingId) {
                return exists;
            }

            @Override
            public boolean isAttendee(Long meetingId, Long memberId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public boolean isHost(Long meetingId, Long memberId) {
                return host;
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

    private SubscribeToCaptionsService service(boolean meetingExists, boolean host) {
        CaptionStreamPort failingStreamPort = (meetingId, memberId) -> {
            throw new AssertionError("인가를 통과하지 못했는데 스트림 포트가 호출되면 안 됩니다.");
        };
        MeetingReferenceRepository meetingRef = meetingRef(meetingExists, host);
        return new SubscribeToCaptionsService(meetingRef, accessGuard(meetingRef), failingStreamPort);
    }

    // 주어진 회의 참조 대역으로 가드를 조립한다(프로젝트 멤버 판정은 이 서비스와 무관해 항상 false).
    private CapMeetingAccessGuard accessGuard(MeetingReferenceRepository meetingRef) {
        return new CapMeetingAccessGuard(meetingRef, (projectId, teamId) -> false);
    }

    private void assertErrorCode(Runnable execution, String expectedCode) {
        assertThatThrownBy(execution::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo(expectedCode);
    }
}
