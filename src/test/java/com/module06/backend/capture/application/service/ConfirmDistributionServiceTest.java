package com.module06.backend.capture.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.port.out.ActionDispatchPort;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.application.port.out.SttGapRepository;
import com.module06.backend.capture.application.result.DistributionConfirmed;
import com.module06.backend.capture.application.result.DistributionConfirmed.SkippedAction;
import com.module06.backend.capture.application.usecase.ConfirmDistributionUseCase.ConfirmDistributionCommand;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RVW-05 · 액션 분배 확정.
 *
 * <p><b>되돌릴 수 없다는 것이 모든 판정의 근거다.</b> 보드로 나간 액션을 회수하는 경로가 없으므로,
 * 이 서비스는 내보내지 않는 쪽으로 기울어야 한다 — 그리고 무엇을 안 내보냈는지 반드시 말해야
 * 한다. 검토가 끝났다고 생각한 회의에서 액션이 조용히 남는 것이 가장 나쁜 실패다.
 */
class ConfirmDistributionServiceTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final long HOST = 42L;
    private static final long NOT_HOST = 43L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 15, 31, 2);

    @Test
    @DisplayName("확정된 액션만 내보낸다 — 반려·담당자 미정은 남기고 이유를 돌려준다")
    void 확정된_액션만_내보낸다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(
                action(1L, "AUTO_CONFIRMED", HOST),
                action(2L, "HUMAN_CONFIRMED", HOST),
                action(3L, "REJECTED", HOST),
                // 담당자 미정 — C 가 AI 분배 경로에서 허용하고, 나가는 것을 여기서 막는다.
                action(4L, "AUTO_CONFIRMED", null));

        DistributionConfirmed confirmed = service(actions, dispatch, 0).confirm(command(HOST, false));

        assertThat(dispatch.dispatched).containsExactly(1L, 2L);
        assertThat(confirmed.dispatchedCount()).isEqualTo(2);
        assertThat(confirmed.dispatchedAt()).isEqualTo(NOW);
        assertThat(confirmed.skipped())
                .extracting(SkippedAction::actionId, SkippedAction::reason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(3L, "REJECTED"),
                        org.assertj.core.groups.Tuple.tuple(4L, "NO_ASSIGNEE"));
    }

    @Test
    @DisplayName("미검토가 남아 있으면 409 로 막는다 — 분배는 되돌릴 수 없다")
    void 미검토가_남으면_막는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(
                action(1L, "AUTO_CONFIRMED", HOST),
                action(2L, "PENDING", HOST));

        assertThatThrownBy(() -> service(actions, dispatch, 0).confirm(command(HOST, false)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_CONFIRM_BLOCKED);

        // 하나도 안 나갔다 — 막혔으면 전부 막힌다.
        assertThat(dispatch.dispatched).isEmpty();
    }

    @Test
    @DisplayName("확인되지 않은 STT 구간이 있으면 막는다 — 아무도 못 들은 구간의 할 일이 사라진다")
    void 미확인_구멍이_있으면_막는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(action(1L, "AUTO_CONFIRMED", HOST));

        assertThatThrownBy(() -> service(actions, dispatch, 2).confirm(command(HOST, false)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_CONFIRM_BLOCKED);
        assertThat(dispatch.dispatched).isEmpty();
    }

    @Test
    @DisplayName("강행해도 미검토는 나가지 않는다 — 관문을 여는 것이지 판정을 바꾸는 게 아니다")
    void 강행해도_미검토는_내보내지_않는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(
                action(1L, "AUTO_CONFIRMED", HOST),
                action(2L, "PENDING", HOST));

        /*
         * 강행이 "검토 안 한 것을 확정한다"가 되면 검토 화면 자체가 무의미해진다.
         * 사람이 무시한 것은 "구멍이 남았다"이지 "이 액션을 확인했다"가 아니다.
         */
        DistributionConfirmed confirmed = service(actions, dispatch, 3).confirm(command(HOST, true));

        assertThat(dispatch.dispatched).containsExactly(1L);
        assertThat(confirmed.skipped())
                .extracting(SkippedAction::reason)
                .containsExactly("STILL_PENDING");
    }

    @Test
    @DisplayName("반려만 남은 회의는 강행 없이도 확정된다 — 반려는 이미 끝난 판단이다")
    void 반려는_관문을_막지_않는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(
                action(1L, "HUMAN_CONFIRMED", HOST),
                action(2L, "REJECTED", HOST));

        /*
         * 반려로 막으면 반려한 액션이 있는 회의는 영원히 강행으로만 확정된다 — 그러면 강행이
         * 일상이 되고, 정작 막아야 할 미검토도 함께 통과한다.
         */
        DistributionConfirmed confirmed = service(actions, dispatch, 0).confirm(command(HOST, false));

        assertThat(confirmed.dispatchedCount()).isEqualTo(1);
        assertThat(confirmed.skipped()).extracting(SkippedAction::reason).containsExactly("REJECTED");
    }

    @Test
    @DisplayName("회의 담당자가 아니면 403 이다 — 마지막 버튼은 한 사람이다")
    void 담당자가_아니면_확정하지_못한다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(action(1L, "AUTO_CONFIRMED", HOST));

        assertThatThrownBy(() -> service(actions, dispatch, 0).confirm(command(NOT_HOST, false)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_CONFIRM_HOST_ONLY);
        assertThat(dispatch.dispatched).isEmpty();
    }

    @Test
    @DisplayName("담당자를 못 읽으면 확정하지 않는다 — 모르는 채 통과시키면 검사가 없는 것이다")
    void 담당자를_모르면_확정하지_않는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        ConfirmDistributionService service = new ConfirmDistributionService(
                new StubQueryPort(List.of(action(1L, "AUTO_CONFIRMED", HOST))), dispatch,
                gaps(0), new MeetingAccessGuard((companyId, meetingId) -> true),
                meetingId -> Optional.empty(), fixedClock());

        assertThatThrownBy(() -> service.confirm(command(HOST, false)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_CONFIRM_HOST_ONLY);
    }

    @Test
    @DisplayName("이미 나간 액션은 다시 세지 않고 사유로 돌려준다 — 실패가 아니라 지난번에 나간 것이다")
    void 이미_나간_액션은_다시_내보내지_않는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        dispatch.alreadyDispatched = List.of(1L, 2L);
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(
                action(1L, "AUTO_CONFIRMED", HOST),
                action(2L, "HUMAN_CONFIRMED", HOST),
                // 확정 뒤에 사람이 새로 넣은 액션(RVW-03)이다. 이번에 나가야 한다.
                action(3L, "HUMAN_CONFIRMED", HOST));

        DistributionConfirmed confirmed = service(actions, dispatch, 0).confirm(command(HOST, false));

        /*
         * dispatchedCount 는 **이번에 새로 나간 것**만이다. 이미 나간 것을 더하면 화면이 같은
         * 액션을 두 번 보낸 것처럼 말한다. 대신 그것들은 사유와 함께 skipped 로 돌려준다 —
         * 안 적으면 사람은 "3건 중 1건만 나갔다"로 읽고 나머지가 실패한 줄 안다.
         */
        assertThat(confirmed.dispatchedCount()).isEqualTo(1);
        assertThat(dispatch.dispatched).containsExactly(3L);
        assertThat(confirmed.skipped())
                .extracting(SkippedAction::actionId, SkippedAction::reason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, "ALREADY_DISPATCHED"),
                        org.assertj.core.groups.Tuple.tuple(2L, "ALREADY_DISPATCHED"));
    }

    @Test
    @DisplayName("내보낼 것이 없으면 포트를 부르지 않는다 — 빈 요청을 만들지 않는다")
    void 대상이_없으면_포트를_부르지_않는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(action(1L, "REJECTED", HOST));

        DistributionConfirmed confirmed = service(actions, dispatch, 0).confirm(command(HOST, false));

        assertThat(dispatch.calls).isZero();
        assertThat(confirmed.dispatchedCount()).isZero();
    }

    private ConfirmDistributionService service(List<ActionReviewQueryPort.ReviewAction> actions,
                                               RecordingDispatchPort dispatch,
                                               int unresolvedGaps) {
        return new ConfirmDistributionService(
                new StubQueryPort(actions),
                dispatch,
                gaps(unresolvedGaps),
                new MeetingAccessGuard((companyId, meetingId) -> true),
                meetingId -> Optional.of(HOST),
                fixedClock());
    }

    /*
     * 미확인 구멍 수만 답하는 가짜다.
     *
     * 람다로 넘기던 자리인데 포트에 쓰기 계약이 붙어(구멍 기록·해소) 함수형이 아니게 됐다.
     * 이 서비스(RVW-05)는 **세는 것만** 한다 — 나머지를 부르면 터뜨려 그 사실을 고정한다.
     */
    private static SttGapRepository gaps(int unresolvedGaps) {
        return new SttGapRepository() {

            @Override
            public int countUnresolved(long meetingId) {
                return unresolvedGaps;
            }

            @Override
            public List<GapView> findByMeeting(long meetingId) {
                throw new UnsupportedOperationException("RVW-05 는 구멍 목록을 읽지 않는다 — 세기만 한다");
            }

            @Override
            public void replaceSttFailureGap(long meetingId, int blockSeq,
                                             int startOffsetMs, int endOffsetMs) {
                throw new UnsupportedOperationException("구멍 기록은 폴링 워커의 몫이다");
            }

            @Override
            public void clearSttFailureGap(long meetingId, int blockSeq) {
                throw new UnsupportedOperationException("구멍 해소는 폴링 워커의 몫이다");
            }

            @Override
            public void replaceRecordingGap(long meetingId, int startOffsetMs, int endOffsetMs,
                                            String reason) {
                throw new UnsupportedOperationException("녹음 구멍 기록은 cap 이 요청한다");
            }
        };
    }

    private Clock fixedClock() {
        ZoneId zone = ZoneId.systemDefault();
        return Clock.fixed(NOW.atZone(zone).toInstant(), zone);
    }

    private ConfirmDistributionCommand command(long requestedBy, boolean force) {
        return new ConfirmDistributionCommand(COMPANY, MEETING, requestedBy, force);
    }

    private ActionReviewQueryPort.ReviewAction action(long actionId, String reviewStatus, Long assignee) {
        return new ActionReviewQueryPort.ReviewAction(
                actionId, assignee, assignee != null ? "김서준" : null, null,
                "로드맵 초안 작성", null, LocalDate.of(2026, 8, 8), false,
                "로드맵", false, reviewStatus, null, null, null, null);
    }

    private record StubQueryPort(List<ActionReviewQueryPort.ReviewAction> actions)
            implements ActionReviewQueryPort {

        @Override
        public List<ReviewAction> findByMeeting(long companyId, long meetingId, String reviewStatus) {
            return actions;
        }

        @Override
        public Optional<ReviewTarget> findOne(long companyId, long meetingId, long actionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<LocalDateTime> dispatchedAtOf(long companyId, long meetingId) {
            throw new UnsupportedOperationException();
        }
    }

    /* 무엇을 내보냈는지가 검증 대상이다 — 특히 "안 내보낸 것"이다. */
    private static final class RecordingDispatchPort implements ActionDispatchPort {

        private final List<Long> dispatched = new ArrayList<>();
        private int calls;
        /* 이미 나가 있던 것으로 취급할 액션. 재확정 상황을 만드는 손잡이다. */
        private List<Long> alreadyDispatched = List.of();

        @Override
        public DispatchOutcome markDispatched(long companyId, List<Long> actionIds,
                                              LocalDateTime dispatchedAt) {
            calls++;
            List<Long> newly = actionIds.stream()
                    .filter(id -> !alreadyDispatched.contains(id))
                    .toList();
            dispatched.addAll(newly);
            return new DispatchOutcome(newly.size(), alreadyDispatched);
        }
    }

}
