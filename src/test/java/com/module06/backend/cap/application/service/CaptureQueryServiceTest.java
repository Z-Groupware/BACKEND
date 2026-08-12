package com.module06.backend.cap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.port.out.CaptureHeartbeatPort;
import com.module06.backend.cap.application.usecase.GetActiveCaptureUseCase;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.ActiveCaptureQueryRepository;
import com.module06.backend.cap.domain.repository.CapCaptureSessionReferenceRepository;

/*
 * CAP-09 진행 중 캡처 조회 서비스가 저장소 결과에 하트비트(canTakeover)와 경과 시간(elapsedMs)을
 * 덧붙여 응답 결과로 변환하는 규칙을 검증하는 단위 테스트다.
 */
@DisplayName("CAP-09 진행 중 캡처 조회 서비스")
class CaptureQueryServiceTest {

    /* 진행 중 캡처가 있으면 상태 필드가 그대로 매핑되고 captureSessionId도 함께 채워지는지 검증한다. */
    @Test
    @DisplayName("진행 중 캡처의 상태 필드와 captureSessionId를 매핑한다")
    void mapsActiveCaptureFields() {
        /* 세그먼트 2, 마지막 청크 41, 녹음자 7번인 진행 중 캡처를 반환하는 서비스를 준비한다. */
        CaptureUploadState state = CaptureUploadState.restore(
                500L, 2, 7L, 41, 3, 0L, LocalDateTime.now().minusSeconds(10), LocalDateTime.now());
        CaptureQueryService service = new CaptureQueryService(found(state), heartbeat(true), session(900L));

        /* 참석자 7번 기준으로 진행 중 캡처를 조회한다. */
        Optional<GetActiveCaptureUseCase.Result> result = service.getActiveCapture(7L);

        /* 상태 필드는 저장소 값 그대로, 세션 ID는 캡처 세션 저장소가 준 값 그대로여야 한다. */
        assertThat(result).isPresent().get().satisfies(data -> {
            assertThat(data.meetingId()).isEqualTo(500L);
            assertThat(data.segmentSeq()).isEqualTo(2);
            assertThat(data.lastSeq()).isEqualTo(41);
            assertThat(data.recorderPersonId()).isEqualTo(7L);
            assertThat(data.captureSessionId()).isEqualTo(900L);
        });
    }

    /* capture_session 행이 아직 없으면(D 쪽 배선이 늦은 경우) captureSessionId가 null로 빠지는지 검증한다. */
    @Test
    @DisplayName("캡처 세션이 없으면 captureSessionId는 null이다")
    void captureSessionIdNullWhenSessionMissing() {
        CaptureQueryService service = new CaptureQueryService(found(sampleState()), heartbeat(true), noSession());

        assertThat(service.getActiveCapture(7L)).isPresent().get()
                .satisfies(data -> assertThat(data.captureSessionId()).isNull());
    }

    /* 하트비트가 살아있으면 canTakeover는 false여야 함을 검증한다. */
    @Test
    @DisplayName("하트비트가 살아있으면 canTakeover는 false다")
    void canTakeoverFalseWhenHeartbeatAlive() {
        CaptureQueryService service = new CaptureQueryService(found(sampleState()), heartbeat(true), noSession());

        assertThat(service.getActiveCapture(7L)).isPresent().get()
                .satisfies(data -> assertThat(data.canTakeover()).isFalse());
    }

    /* 하트비트가 끊겼으면 canTakeover는 true여야 함을 검증한다(이어받기 버튼 노출 근거). */
    @Test
    @DisplayName("하트비트가 끊겼으면 canTakeover는 true다")
    void canTakeoverTrueWhenHeartbeatDead() {
        CaptureQueryService service = new CaptureQueryService(found(sampleState()), heartbeat(false), noSession());

        assertThat(service.getActiveCapture(7L)).isPresent().get()
                .satisfies(data -> assertThat(data.canTakeover()).isTrue());
    }

    /* elapsedMs가 캡처 시작(createdAt) 이후 경과로 계산되는지 검증한다. */
    @Test
    @DisplayName("elapsedMs는 캡처 시작(createdAt) 이후 경과 시간으로 계산한다")
    void computesElapsedFromCreatedAt() {
        /* 10초 전에 시작된 캡처를 준비한다. */
        CaptureUploadState state = CaptureUploadState.restore(
                500L, 0, 7L, 0, 0, 0L, LocalDateTime.now().minusSeconds(10), LocalDateTime.now());
        CaptureQueryService service = new CaptureQueryService(found(state), heartbeat(true), noSession());

        /* 경과는 최소 10초 이상이며 비정상적으로 크지 않아야 한다. */
        assertThat(service.getActiveCapture(7L)).isPresent().get()
                .satisfies(data -> assertThat(data.elapsedMs())
                        .isGreaterThanOrEqualTo(10_000L)
                        .isLessThan(60_000L));
    }

    /* createdAt이 없으면 elapsedMs가 음수로 새지 않고 0으로 바닥 처리되는지 검증한다. */
    @Test
    @DisplayName("createdAt이 없으면 elapsedMs는 0이다")
    void elapsedZeroWhenCreatedAtMissing() {
        CaptureUploadState state = CaptureUploadState.restore(500L, 0, 7L, 0, 0, 0L, null, null);
        CaptureQueryService service = new CaptureQueryService(found(state), heartbeat(true), noSession());

        assertThat(service.getActiveCapture(7L)).isPresent().get()
                .satisfies(data -> assertThat(data.elapsedMs()).isZero());
    }

    /* 진행 중 캡처가 없으면 빈 결과를 반환하는지 검증한다(응답 data:null). */
    @Test
    @DisplayName("진행 중 캡처가 없으면 빈 결과를 반환한다")
    void returnsEmptyWhenNoActiveCapture() {
        CaptureQueryService service = new CaptureQueryService(memberId -> Optional.empty(), heartbeat(true), noSession());

        assertThat(service.getActiveCapture(7L)).isEmpty();
    }

    /* 지정한 캡처 상태를 그대로 반환하는 조회 저장소 대역을 만든다. */
    private ActiveCaptureQueryRepository found(CaptureUploadState state) {
        return memberId -> Optional.of(state);
    }

    /* 하트비트 생존 여부를 고정 반환하는 포트 대역을 만든다. */
    private CaptureHeartbeatPort heartbeat(boolean alive) {
        return new CaptureHeartbeatPort() {
            @Override
            public void refresh(Long meetingId) {
                /* 조회 경로에서는 갱신을 호출하지 않는다. */
            }

            @Override
            public boolean isAlive(Long meetingId) {
                return alive;
            }
        };
    }

    /* 필드 매핑과 무관한 테스트에서 재사용할 기본 진행 중 캡처를 만든다. */
    private CaptureUploadState sampleState() {
        return CaptureUploadState.restore(500L, 0, 7L, 10, 0, 0L,
                LocalDateTime.now().minusSeconds(5), LocalDateTime.now());
    }

    /* 지정한 세션 id를 반환하는 캡처 세션 저장소 대역을 만든다. */
    private CapCaptureSessionReferenceRepository session(Long sessionId) {
        return new CapCaptureSessionReferenceRepository() {
            @Override
            public boolean isPaused(Long meetingId) {
                throw new UnsupportedOperationException("이 테스트는 대상 밖입니다.");
            }

            @Override
            public Optional<Long> findSessionId(Long meetingId) {
                return Optional.of(sessionId);
            }
        };
    }

    /* capture_session 행이 없는(D 쪽 배선이 늦은) 경우를 흉내내는 대역을 만든다. */
    private CapCaptureSessionReferenceRepository noSession() {
        return new CapCaptureSessionReferenceRepository() {
            @Override
            public boolean isPaused(Long meetingId) {
                return false;
            }

            @Override
            public Optional<Long> findSessionId(Long meetingId) {
                return Optional.empty();
            }
        };
    }
}
