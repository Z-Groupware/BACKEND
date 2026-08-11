package com.module06.backend.capture.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.module06.backend.capture.application.port.out.CustomVocabularyPort;
import com.module06.backend.capture.application.port.out.CustomVocabularyPort.VocabularyState;
import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository;
import com.module06.backend.capture.application.port.out.MeetingVocabularyRepository.VocabularyView;
import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.application.port.out.SttBlockRepository.SttBlockView;
import com.module06.backend.capture.domain.model.SttBlockStatus;
import com.module06.backend.capture.domain.model.SttCutReason;
import com.module06.backend.capture.domain.model.VocabularyStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 커스텀 어휘의 완료 확인(승격)과 정리(삭제).
 *
 * <p>검증의 축이 둘이다. <b>계정 상한을 지키는가</b> — 이전 리소스를 안 지우면 재생성마다 하나씩
 * 쌓이고, 상한에 걸린 회의는 아무 오류 없이 인식률만 낮아진다. 그리고 <b>도는 잡의 어휘를
 * 지우지 않는가</b> — 제출된 잡이 이름을 참조하므로 지우면 그 회의의 인식률이 조용히 떨어진다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VocabularyLifecycleServiceTest {

    private static final long VOCAB_ID = 5L;
    private static final long MEETING = 500L;
    private static final String PENDING_NAME = "meeting-500-vocab-20260810150000";
    private static final String ACTIVE_NAME = "meeting-500-vocab-20260801090000";

    @Mock
    private MeetingVocabularyRepository meetingVocabularyRepository;

    @Mock
    private CustomVocabularyPort customVocabularyPort;

    @Mock
    private SttBlockRepository sttBlockRepository;

    private VocabularyLifecycleService service() {
        return new VocabularyLifecycleService(
                meetingVocabularyRepository, customVocabularyPort, sttBlockRepository);
    }

    // ── 승격 ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("READY 면 승격하고 이전 리소스를 지운다 — 안 지우면 재생성마다 상한이 하나씩 준다")
    void 승격하고_이전_리소스를_지운다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.READY);
        when(meetingVocabularyRepository.promoteToReady(anyLong(), anyInt()))
                .thenReturn(Optional.of(ACTIVE_NAME));

        assertThat(service().promoteReadyOnce()).isEqualTo(1);

        /*
         * 순서가 중요하다 — 먼저 승격해 새 이름이 활성이 되고 그 뒤에 이전 것을 지운다.
         * 반대로 하면 삭제 성공 + 승격 실패에서 활성 어휘가 없는 채로 이름만 남는다.
         */
        InOrder order = inOrder(meetingVocabularyRepository, customVocabularyPort);
        order.verify(meetingVocabularyRepository).promoteToReady(VOCAB_ID, 214);
        order.verify(customVocabularyPort).delete(ACTIVE_NAME);
    }

    @Test
    @DisplayName("첫 생성이면 지울 이전 리소스가 없다")
    void 첫_생성이면_지우지_않는다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.READY);
        when(meetingVocabularyRepository.promoteToReady(anyLong(), anyInt())).thenReturn(Optional.empty());

        service().promoteReadyOnce();

        verify(customVocabularyPort, never()).delete(anyString());
    }

    @Test
    @DisplayName("아직 만드는 중이면 아무것도 하지 않는다")
    void 만드는_중이면_그대로_둔다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.PENDING);

        assertThat(service().promoteReadyOnce()).isZero();

        verify(meetingVocabularyRepository, never()).promoteToReady(anyLong(), anyInt());
        verify(meetingVocabularyRepository, never()).markBuildFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("⚠ 못 읽었으면 상태를 바꾸지 않는다 — 실패로 접으면 재생성이 리소스를 하나 더 만든다")
    void 못_읽으면_상태를_바꾸지_않는다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.UNAVAILABLE);

        assertThat(service().promoteReadyOnce()).isZero();

        verify(meetingVocabularyRepository, never()).markBuildFailed(anyLong(), anyString());
        verify(meetingVocabularyRepository, never()).promoteToReady(anyLong(), anyInt());
    }

    @Test
    @DisplayName("제공자가 실패로 닫으면 FAILED — PENDING 으로 두면 다시 누를 수도 없다")
    void 제공자_실패는_FAILED로_닫는다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.FAILED);

        assertThat(service().promoteReadyOnce()).isEqualTo(1);

        // 선점이 PENDING 을 막으므로 PENDING 으로 두면 사람이 재생성을 누를 수도 없다.
        verify(meetingVocabularyRepository).markBuildFailed(VOCAB_ID, "PROVIDER_FAILED");
    }

    @Test
    @DisplayName("제공자가 모르는 어휘도 FAILED — 그대로 두면 영원히 만드는 중이다")
    void 모르는_어휘는_FAILED로_닫는다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.UNKNOWN);

        assertThat(service().promoteReadyOnce()).isEqualTo(1);

        verify(meetingVocabularyRepository).markBuildFailed(VOCAB_ID, "VOCABULARY_NOT_FOUND");
    }

    // ── 정리 ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("받아쓰기가 끝난 회의의 어휘를 지운다")
    void 끝난_회의의_어휘를_지운다() {
        givenCleanupTarget();
        when(sttBlockRepository.findByMeeting(MEETING)).thenReturn(List.of(block(SttBlockStatus.DONE)));
        when(sttBlockRepository.countUnfinished(MEETING)).thenReturn(0);

        assertThat(service().cleanupOnce()).isEqualTo(1);

        verify(customVocabularyPort).delete(ACTIVE_NAME);
        // 표시를 안 하면 매 주기 같은 대상을 다시 집어 제공자 호출만 늘어난다.
        verify(meetingVocabularyRepository).markCleaned(VOCAB_ID);
    }

    @Test
    @DisplayName("⚠ 받아쓰기가 도는 중이면 지우지 않는다 — 제출된 잡이 이 이름을 참조한다")
    void 도는_중이면_지우지_않는다() {
        givenCleanupTarget();
        when(sttBlockRepository.findByMeeting(MEETING)).thenReturn(List.of(block(SttBlockStatus.QUEUED)));
        when(sttBlockRepository.countUnfinished(MEETING)).thenReturn(1);

        assertThat(service().cleanupOnce()).isZero();

        /*
         * 도는 중에 지우면 그 잡이 어휘 없이 돌거나 실패한다 — 회의 하나 분량의 인식률이
         * 조용히 떨어지는 경로다.
         */
        verify(customVocabularyPort, never()).delete(anyString());
        verify(meetingVocabularyRepository, never()).markCleaned(anyLong());
    }

    @Test
    @DisplayName("블록이 아예 없는 회의는 지우지 않는다 — 아직 녹음하지 않은 예약 회의다")
    void 녹음_전_회의는_지우지_않는다() {
        givenCleanupTarget();
        when(sttBlockRepository.findByMeeting(MEETING)).thenReturn(List.of());
        when(sttBlockRepository.countUnfinished(MEETING)).thenReturn(0);

        assertThat(service().cleanupOnce()).isZero();

        // 어휘는 그 회의를 위해 만든 것이고, 지우면 정작 필요할 때 없다.
        verify(customVocabularyPort, never()).delete(anyString());
    }

    @Test
    @DisplayName("어휘 하나가 터져도 나머지를 처리한다")
    void 하나의_실패가_나머지를_막지_않는다() {
        VocabularyView broken = view(1L, 1L, VocabularyStatus.PENDING, "job-broken");
        VocabularyView healthy = view(2L, 2L, VocabularyStatus.PENDING, "job-healthy");
        when(meetingVocabularyRepository.findPendingBuilds(anyInt()))
                .thenReturn(List.of(broken, healthy));

        when(customVocabularyPort.stateOf("job-broken")).thenThrow(new IllegalStateException("터짐"));
        when(customVocabularyPort.stateOf("job-healthy")).thenReturn(VocabularyState.FAILED);

        assertThat(service().promoteReadyOnce()).isEqualTo(1);

        verify(meetingVocabularyRepository).markBuildFailed(2L, "PROVIDER_FAILED");
    }

    @Test
    @DisplayName("대상이 없으면 제공자를 부르지 않는다")
    void 대상이_없으면_부르지_않는다() {
        when(meetingVocabularyRepository.findPendingBuilds(anyInt())).thenReturn(List.of());
        when(meetingVocabularyRepository.findCleanupTargets(anyInt())).thenReturn(List.of());

        assertThat(service().promoteReadyOnce()).isZero();
        assertThat(service().cleanupOnce()).isZero();

        verify(customVocabularyPort, never()).stateOf(anyString());
        verify(customVocabularyPort, never()).delete(anyString());
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private void givenPending() {
        when(meetingVocabularyRepository.findPendingBuilds(anyInt()))
                .thenReturn(List.of(view(VOCAB_ID, MEETING, VocabularyStatus.PENDING, PENDING_NAME)));
    }

    private void givenCleanupTarget() {
        when(meetingVocabularyRepository.findCleanupTargets(anyInt()))
                .thenReturn(List.of(new VocabularyView(VOCAB_ID, MEETING, VocabularyStatus.READY, 214,
                        ACTIVE_NAME, LocalDateTime.of(2026, 8, 1, 9, 0), null, false)));
    }

    private static VocabularyView view(long id, long meetingId, VocabularyStatus status, String pendingName) {
        return new VocabularyView(id, meetingId, status, 214, ACTIVE_NAME,
                LocalDateTime.of(2026, 8, 1, 9, 0), pendingName, false);
    }

    private static SttBlockView block(SttBlockStatus status) {
        return new SttBlockView(1L, 0, 0, 600_000, status, "aws-transcribe",
                SttCutReason.VAD_SILENCE, 0, null, "stt-temp/org-1/meeting-500/blocks/0.wav",
                null, null);
    }
}
