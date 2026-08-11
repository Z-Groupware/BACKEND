package com.module06.backend.capture.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 커스텀 어휘의 완료 확인(승격) · 정리(삭제) · 포기.
 *
 * <p>검증의 축이 셋이다.
 * <ul>
 *   <li><b>계정 상한을 지키는가</b> — 밀려난 리소스를 안 지우면 재생성마다 하나씩 쌓이고,
 *       상한에 걸린 회의는 아무 오류 없이 인식률만 낮아진다</li>
 *   <li><b>도는 잡의 어휘를 지우지 않는가</b> — 제출된 잡이 이름을 참조한다</li>
 *   <li><b>옛 폴링 결과가 새 빌드를 덮지 않는가</b> — 그러면 만들어지지도 않은 리소스가
 *       활성이 되고 받아쓰기 전체가 실패한다</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VocabularyLifecycleServiceTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-08-11T06:00:00Z"), ZoneId.of("Asia/Seoul"));

    private static final long VOCAB_ID = 5L;
    private static final long MEETING = 500L;
    private static final String PENDING_NAME = "meeting-500-vocab-20260811150000";
    private static final String ACTIVE_NAME = "meeting-500-vocab-20260801090000";

    @Mock
    private MeetingVocabularyRepository meetingVocabularyRepository;

    @Mock
    private CustomVocabularyPort customVocabularyPort;

    @Mock
    private SttBlockRepository sttBlockRepository;

    @Captor
    private ArgumentCaptor<LocalDateTime> thresholdCaptor;

    private VocabularyLifecycleService service() {
        return new VocabularyLifecycleService(
                meetingVocabularyRepository, customVocabularyPort, sttBlockRepository, FIXED);
    }

    // ── 승격 ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("READY 면 승격한다 — 폴링한 이름을 함께 넘겨 그 빌드가 맞는지 확인시킨다")
    void 승격한다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.READY);
        when(meetingVocabularyRepository.promoteToReady(VOCAB_ID, PENDING_NAME)).thenReturn(true);

        assertThat(service().promoteReadyOnce()).isEqualTo(1);

        verify(meetingVocabularyRepository).promoteToReady(VOCAB_ID, PENDING_NAME);
    }

    @Test
    @DisplayName("⚠ 승격 자리에서 이전 리소스를 지우지 않는다 — 삭제 실패 시 이름이 사라진다")
    void 승격은_지우지_않는다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.READY);
        when(meetingVocabularyRepository.promoteToReady(anyLong(), anyString())).thenReturn(true);

        service().promoteReadyOnce();

        /*
         * 여기서 지우면 두 가지가 깨진다 — 삭제가 실패했을 때 다시 시도할 이름이 없고(V5.19 가
         * 경고한 누수), 아직 도는 STT 잡이 그 이름을 참조할 수 있는데 이 경로는 그 검사를
         * 하지 않는다. 밀려난 이름은 저장소가 stale 칸에 적어 두고 정리 워커가 지운다.
         */
        verify(customVocabularyPort, never()).delete(anyString());
    }

    @Test
    @DisplayName("⚠ 그 사이 새 빌드가 접수됐으면 승격하지 않는다 — 안 만들어진 리소스가 활성이 된다")
    void 새_빌드가_접수됐으면_물러난다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.READY);
        // 저장소가 대기 이름을 다시 확인해 다르면 물러난다(compare-and-set).
        when(meetingVocabularyRepository.promoteToReady(anyLong(), anyString())).thenReturn(false);

        assertThat(service().promoteReadyOnce()).isZero();
    }

    @Test
    @DisplayName("아직 만드는 중이면 아무것도 하지 않는다")
    void 만드는_중이면_그대로_둔다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.PENDING);

        assertThat(service().promoteReadyOnce()).isZero();

        verify(meetingVocabularyRepository, never()).promoteToReady(anyLong(), anyString());
        verify(meetingVocabularyRepository, never())
                .markBuildFailedIfPending(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("⚠ 못 읽었으면 상태를 바꾸지 않는다 — 실패로 접으면 재생성이 리소스를 하나 더 만든다")
    void 못_읽으면_상태를_바꾸지_않는다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.UNAVAILABLE);

        assertThat(service().promoteReadyOnce()).isZero();

        verify(meetingVocabularyRepository, never())
                .markBuildFailedIfPending(anyLong(), anyString(), anyString());
        verify(meetingVocabularyRepository, never()).promoteToReady(anyLong(), anyString());
    }

    @Test
    @DisplayName("제공자가 실패로 닫으면 FAILED — PENDING 으로 두면 다시 누를 수도 없다")
    void 제공자_실패는_FAILED로_닫는다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.FAILED);
        when(meetingVocabularyRepository.markBuildFailedIfPending(anyLong(), anyString(), anyString()))
                .thenReturn(true);

        assertThat(service().promoteReadyOnce()).isEqualTo(1);

        // 선점이 PENDING 을 막으므로 PENDING 으로 두면 사람이 재생성을 누를 수도 없다.
        verify(meetingVocabularyRepository)
                .markBuildFailedIfPending(VOCAB_ID, PENDING_NAME, "PROVIDER_FAILED");
    }

    @Test
    @DisplayName("제공자가 모르는 어휘도 FAILED — 그대로 두면 영원히 만드는 중이다")
    void 모르는_어휘는_FAILED로_닫는다() {
        givenPending();
        when(customVocabularyPort.stateOf(PENDING_NAME)).thenReturn(VocabularyState.UNKNOWN);
        when(meetingVocabularyRepository.markBuildFailedIfPending(anyLong(), anyString(), anyString()))
                .thenReturn(true);

        assertThat(service().promoteReadyOnce()).isEqualTo(1);

        verify(meetingVocabularyRepository)
                .markBuildFailedIfPending(VOCAB_ID, PENDING_NAME, "VOCABULARY_NOT_FOUND");
    }

    // ── 포기 ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("응답 없이 오래 걸린 빌드를 포기한다 — 안 하면 뒤 항목의 승격이 계속 밀린다")
    void 멈춘_빌드를_포기한다() {
        when(meetingVocabularyRepository.findStuckBuilds(thresholdCaptor.capture(), anyInt()))
                .thenReturn(List.of(pendingView()));
        when(meetingVocabularyRepository.markBuildFailedIfPending(anyLong(), anyString(), anyString()))
                .thenReturn(true);

        assertThat(service().abandonStuckOnce()).isEqualTo(1);

        /*
         * 상한을 넉넉히(30분) 잡는다 — 짧게 잡으면 정상적으로 만들어지는 중인 어휘를 포기해
         * 사람이 다시 눌러 리소스가 하나 더 만들어진다. 오탐의 대가가 더 크다.
         */
        assertThat(thresholdCaptor.getValue())
                .isEqualTo(LocalDateTime.now(FIXED).minusMinutes(30));
        verify(meetingVocabularyRepository)
                .markBuildFailedIfPending(VOCAB_ID, PENDING_NAME, "BUILD_TIMEOUT");
    }

    // ── 활성 정리 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("받아쓰기가 끝난 회의의 어휘를 지운다")
    void 끝난_회의의_어휘를_지운다() {
        when(meetingVocabularyRepository.findCleanupTargets(anyInt())).thenReturn(List.of(readyView()));
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
        when(meetingVocabularyRepository.findCleanupTargets(anyInt())).thenReturn(List.of(readyView()));
        when(sttBlockRepository.findByMeeting(MEETING)).thenReturn(List.of(block(SttBlockStatus.QUEUED)));
        when(sttBlockRepository.countUnfinished(MEETING)).thenReturn(1);

        assertThat(service().cleanupOnce()).isZero();

        verify(customVocabularyPort, never()).delete(anyString());
        verify(meetingVocabularyRepository, never()).markCleaned(anyLong());
    }

    @Test
    @DisplayName("블록이 아예 없는 회의는 지우지 않는다 — 아직 녹음하지 않은 예약 회의다")
    void 녹음_전_회의는_지우지_않는다() {
        when(meetingVocabularyRepository.findCleanupTargets(anyInt())).thenReturn(List.of(readyView()));
        when(sttBlockRepository.findByMeeting(MEETING)).thenReturn(List.of());
        when(sttBlockRepository.countUnfinished(MEETING)).thenReturn(0);

        assertThat(service().cleanupOnce()).isZero();

        // 어휘는 그 회의를 위해 만든 것이고, 지우면 정작 필요할 때 없다.
        verify(customVocabularyPort, never()).delete(anyString());
    }

    // ── 밀려난 리소스 정리 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("밀려난 리소스를 지우고 이름 칸을 비운다 — 안 비우면 매 주기 같은 대상을 다시 집는다")
    void 밀려난_리소스를_지운다() {
        when(meetingVocabularyRepository.findStaleTargets(anyInt())).thenReturn(List.of(staleView()));
        when(sttBlockRepository.countUnfinished(MEETING)).thenReturn(0);

        assertThat(service().cleanupStaleOnce()).isEqualTo(1);

        verify(customVocabularyPort).delete(ACTIVE_NAME);
        verify(meetingVocabularyRepository).clearStaleName(VOCAB_ID);
    }

    @Test
    @DisplayName("⚠ 밀려난 것도 받아쓰기가 도는 중이면 미룬다 — 재생성 전에 제출된 잡이 참조한다")
    void 밀려난_리소스도_도는_중이면_미룬다() {
        when(meetingVocabularyRepository.findStaleTargets(anyInt())).thenReturn(List.of(staleView()));
        when(sttBlockRepository.countUnfinished(MEETING)).thenReturn(2);

        assertThat(service().cleanupStaleOnce()).isZero();

        /*
         * 승격 자리에서 곧바로 지웠다면 이 검사를 건너뛰었을 것이다 — 그게 정리를 별도 경로로
         * 옮긴 이유다.
         */
        verify(customVocabularyPort, never()).delete(anyString());
        verify(meetingVocabularyRepository, never()).clearStaleName(anyLong());
    }

    // ── 공통 ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("어휘 하나가 터져도 나머지를 처리한다")
    void 하나의_실패가_나머지를_막지_않는다() {
        VocabularyView broken = view(1L, 1L, VocabularyStatus.PENDING, "job-broken", null, null);
        VocabularyView healthy = view(2L, 2L, VocabularyStatus.PENDING, "job-healthy", null, null);
        when(meetingVocabularyRepository.findPendingBuilds(anyInt()))
                .thenReturn(List.of(broken, healthy));

        when(customVocabularyPort.stateOf("job-broken")).thenThrow(new IllegalStateException("터짐"));
        when(customVocabularyPort.stateOf("job-healthy")).thenReturn(VocabularyState.FAILED);
        when(meetingVocabularyRepository.markBuildFailedIfPending(eq(2L), anyString(), anyString()))
                .thenReturn(true);

        assertThat(service().promoteReadyOnce()).isEqualTo(1);

        verify(meetingVocabularyRepository)
                .markBuildFailedIfPending(2L, "job-healthy", "PROVIDER_FAILED");
    }

    @Test
    @DisplayName("대상이 없으면 제공자를 부르지 않는다")
    void 대상이_없으면_부르지_않는다() {
        when(meetingVocabularyRepository.findPendingBuilds(anyInt())).thenReturn(List.of());
        when(meetingVocabularyRepository.findCleanupTargets(anyInt())).thenReturn(List.of());
        when(meetingVocabularyRepository.findStaleTargets(anyInt())).thenReturn(List.of());

        assertThat(service().promoteReadyOnce()).isZero();
        assertThat(service().cleanupOnce()).isZero();
        assertThat(service().cleanupStaleOnce()).isZero();

        verify(customVocabularyPort, never()).stateOf(anyString());
        verify(customVocabularyPort, never()).delete(anyString());
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private void givenPending() {
        when(meetingVocabularyRepository.findPendingBuilds(anyInt())).thenReturn(List.of(pendingView()));
    }

    /* 만들어지는 중 — 대기 이름이 있고 활성 이름도 있다(재생성). */
    private static VocabularyView pendingView() {
        return view(VOCAB_ID, MEETING, VocabularyStatus.PENDING, PENDING_NAME, ACTIVE_NAME, null);
    }

    /* 활성 정리 대상 — 대기 이름이 없고 활성 이름만 있다. */
    private static VocabularyView readyView() {
        return view(VOCAB_ID, MEETING, VocabularyStatus.READY, null, ACTIVE_NAME, null);
    }

    /* 밀려난 리소스가 남은 행. */
    private static VocabularyView staleView() {
        return view(VOCAB_ID, MEETING, VocabularyStatus.READY, null, PENDING_NAME, ACTIVE_NAME);
    }

    private static VocabularyView view(long id, long meetingId, VocabularyStatus status,
                                       String pendingName, String activeName, String staleName) {
        return new VocabularyView(id, meetingId, status, 214, activeName,
                LocalDateTime.of(2026, 8, 1, 9, 0), pendingName, false, staleName);
    }

    private static SttBlockView block(SttBlockStatus status) {
        return new SttBlockView(1L, 0, 0, 600_000, status, "aws-transcribe",
                SttCutReason.VAD_SILENCE, 0, null, "stt-temp/org-1/meeting-500/blocks/0.wav",
                null, null);
    }
}
