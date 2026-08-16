package com.module06.backend.capture.application.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.capture.application.port.out.ActionDispatchPort;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.application.port.out.SttGapRepository;
import com.module06.backend.capture.application.result.DistributionConfirmed;
import com.module06.backend.capture.application.result.DistributionConfirmed.SkippedAction;
import com.module06.backend.capture.application.result.ReviewDecisionOutcome;
import com.module06.backend.capture.application.usecase.ApplyReviewDecisionUseCase;
import com.module06.backend.capture.application.usecase.ApplyReviewDecisionUseCase.ReviewDecisionCommand;
import com.module06.backend.capture.application.usecase.ConfirmDistributionUseCase.ConfirmDistributionCommand;
import com.module06.backend.capture.domain.model.ReviewDecision;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.meeting.application.port.in.MeetingActionConfirmationPort;

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
        /*
         * 확정 두 건을 나란히 두는 이유 — 하나만 내보내고 끝나는 구현을 잡는다.
         *
         * 예전에는 1번이 AUTO_CONFIRMED 였다. "확정 상태 두 종류가 모두 나간다"를 보는
         * 자리였는데, #418 이 그 값을 지워(쓰는 코드가 없던 죽은 값이었다) 확정 상태가
         * 하나만 남았다. 그래서 이 목록은 이제 "확정된 것이 여러 건이면 여러 건 다 나간다"를
         * 본다.
         */
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(
                action(1L, "HUMAN_CONFIRMED", HOST),
                action(2L, "HUMAN_CONFIRMED", HOST),
                action(3L, "REJECTED", HOST),
                // 담당자 미정 — C 가 AI 분배 경로에서 허용하고, 나가는 것을 여기서 막는다.
                action(4L, "HUMAN_CONFIRMED", null));

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
    @DisplayName("액션과 회의에 동일한 최초 분배 확정 시각을 전달한다")
    void 회의에도_동일한_확정_시각을_전달한다() {
        /* 회의 도메인에 전달된 회사·회의·확정 시각을 기록하는 Port 대역을 준비한다. */
        RecordingMeetingActionConfirmationPort confirmationPort =
                new RecordingMeetingActionConfirmationPort();
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        ConfirmDistributionService service = new ConfirmDistributionService(
                new StubQueryPort(List.of(action(1L, "HUMAN_CONFIRMED", HOST))),
                dispatch,
                gaps(0),
                new MeetingAccessGuard((companyId, meetingId) -> true),
                meetingId -> Optional.of(HOST),
                new RecordingApplyReviewDecisionUseCase(),
                confirmationPort,
                fixedClock()
        );

        /* 분배 확정을 실행해 액션과 회의에 한 번 읽은 시각을 함께 반영한다. */
        service.confirm(command(HOST, false));

        /* D에는 요청 테넌트·회의와 액션에 사용한 정확히 같은 NOW가 전달돼야 한다. */
        assertThat(confirmationPort.companyId).isEqualTo(COMPANY);
        assertThat(confirmationPort.meetingId).isEqualTo(MEETING);
        assertThat(confirmationPort.confirmedAt).isEqualTo(NOW);
        assertThat(dispatch.dispatchedAt).isEqualTo(NOW);
    }

    @Test
    @DisplayName("미검토가 남아 있으면 409 로 막는다 — 분배는 되돌릴 수 없다")
    void 미검토가_남으면_막는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        // 담당자 없는 PENDING만 진짜 "미검토"로 남는다 — 담당자 있는 PENDING은 2026-08-11부터
        // 확정 호출 자체가 암묵 CONFIRM 처리하므로 여기서 막히지 않는다(아래 새 테스트 참고).
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(
                action(1L, "HUMAN_CONFIRMED", HOST),
                action(2L, "PENDING", null));

        assertThatThrownBy(() -> service(actions, dispatch, 0).confirm(command(HOST, false)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_CONFIRM_BLOCKED);

        // 하나도 안 나갔다 — 막혔으면 전부 막힌다.
        assertThat(dispatch.dispatched).isEmpty();
    }

    @Test
    @DisplayName("2026-08-11 — 담당자 있는 PENDING은 확정 버튼 한 번으로 암묵 CONFIRM되어 함께 나간다")
    void 담당자_있는_미검토는_확정_버튼_한번으로_함께_나간다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        RecordingApplyReviewDecisionUseCase applyUseCase = new RecordingApplyReviewDecisionUseCase();
        StubQueryPort query = new StubQueryPort(List.of(
                action(1L, "HUMAN_CONFIRMED", HOST),
                action(2L, "PENDING", HOST)));
        applyUseCase.attach(query);
        ConfirmDistributionService service = new ConfirmDistributionService(
                query, dispatch, gaps(0), new MeetingAccessGuard((companyId, meetingId) -> true),
                meetingId -> Optional.of(HOST), applyUseCase,
                (companyId, meetingId, confirmedAt) -> { }, fixedClock());

        DistributionConfirmed confirmed = service.confirm(command(HOST, false));

        assertThat(applyUseCase.confirmedActionIds).containsExactly(2L);
        assertThat(dispatch.dispatched).containsExactlyInAnyOrder(1L, 2L);
        assertThat(confirmed.skipped()).isEmpty();
    }

    @Test
    @DisplayName("2026-08-15 — 코드가 이어 준 담당자는 암묵 확정되지 않는다, 사람이 명시적으로 판정해야 나간다")
    void 근접_매칭_담당자는_암묵_확정되지_않는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        RecordingApplyReviewDecisionUseCase applyUseCase = new RecordingApplyReviewDecisionUseCase();
        /*
         * 1번은 모델이 정한 담당자, 2번은 근접 매칭이 이어 준 담당자다. 둘 다 담당자가 있는
         * PENDING 이라 예전에는 구분 없이 함께 나갔다 — 그게 #522 로 생긴 구멍이다.
         */
        StubQueryPort query = new StubQueryPort(List.of(
                action(1L, "PENDING", HOST),
                action(2L, "PENDING", HOST, true)));
        applyUseCase.attach(query);
        ConfirmDistributionService service = new ConfirmDistributionService(
                query, dispatch, gaps(0), new MeetingAccessGuard((companyId, meetingId) -> true),
                meetingId -> Optional.of(HOST), applyUseCase,
                (companyId, meetingId, confirmedAt) -> { }, fixedClock());

        // 2번이 미검토로 남으므로 관문이 막는다 — 근접 매칭 이전과 같은 상태다.
        assertThatThrownBy(() -> service.confirm(command(HOST, false)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_CONFIRM_BLOCKED);

        // 암묵 확정은 1번에만 걸렸다. 2번은 손대지 않았다.
        assertThat(applyUseCase.confirmedActionIds).containsExactly(1L);
        assertThat(dispatch.dispatched).isEmpty();
    }

    @Test
    @DisplayName("강행해도 근접 매칭 담당자는 나가지 않는다 — 추측으로 채운 값이 보드로 못 간다")
    void 강행해도_근접_매칭_담당자는_나가지_않는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(
                action(1L, "HUMAN_CONFIRMED", HOST),
                action(2L, "PENDING", HOST, true));

        DistributionConfirmed confirmed = service(actions, dispatch, 0).confirm(command(HOST, true));

        // 보드로 나간 액션은 회수할 수 없다. 강행은 관문을 여는 것이지 판정을 바꾸는 게 아니다.
        assertThat(dispatch.dispatched).containsExactly(1L);
        assertThat(confirmed.skipped())
                .extracting(SkippedAction::actionId, SkippedAction::reason)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(2L, "STILL_PENDING"));
    }

    @Test
    @DisplayName("근접 매칭 여부가 NULL 이면 막지 않는다 — 이 코드 이전에 저장된 배정이다")
    void 근접_매칭_미수행은_막지_않는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        RecordingApplyReviewDecisionUseCase applyUseCase = new RecordingApplyReviewDecisionUseCase();
        StubQueryPort query = new StubQueryPort(List.of(action(1L, "PENDING", HOST, null)));
        applyUseCase.attach(query);
        ConfirmDistributionService service = new ConfirmDistributionService(
                query, dispatch, gaps(0), new MeetingAccessGuard((companyId, meetingId) -> true),
                meetingId -> Optional.of(HOST), applyUseCase,
                (companyId, meetingId, confirmedAt) -> { }, fixedClock());

        DistributionConfirmed confirmed = service.confirm(command(HOST, false));

        // NULL 까지 막으면 과거 회의의 확정이 통째로 멈춘다 — 조이는 것이 아니라 막아버리는 것이다.
        assertThat(applyUseCase.confirmedActionIds).containsExactly(1L);
        assertThat(dispatch.dispatched).containsExactly(1L);
        assertThat(confirmed.skipped()).isEmpty();
    }

    @Test
    @DisplayName("사람이 이미 판정한 근접 매칭 건은 그대로 나간다 — 막는 것은 암묵 확정뿐이다")
    void 사람이_판정한_근접_매칭_건은_나간다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(
                action(1L, "HUMAN_CONFIRMED", HOST, true));

        DistributionConfirmed confirmed = service(actions, dispatch, 0).confirm(command(HOST, false));

        // host 가 수용을 눌렀으면 코드가 이었다는 사실은 더 이상 관문이 아니다.
        assertThat(dispatch.dispatched).containsExactly(1L);
        assertThat(confirmed.skipped()).isEmpty();
    }

    @Test
    @DisplayName("확인되지 않은 STT 구간이 있으면 막는다 — 아무도 못 들은 구간의 할 일이 사라진다")
    void 미확인_구멍이_있으면_막는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(action(1L, "HUMAN_CONFIRMED", HOST));

        assertThatThrownBy(() -> service(actions, dispatch, 2).confirm(command(HOST, false)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", CaptureErrorCode.REVIEW_CONFIRM_BLOCKED);
        assertThat(dispatch.dispatched).isEmpty();
    }

    @Test
    @DisplayName("강행해도 미검토는 나가지 않는다 — 관문을 여는 것이지 판정을 바꾸는 게 아니다")
    void 강행해도_미검토는_내보내지_않는다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        // 담당자 없어 암묵 CONFIRM 대상이 아닌 진짜 미검토 항목.
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(
                action(1L, "HUMAN_CONFIRMED", HOST),
                action(2L, "PENDING", null));

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
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(action(1L, "HUMAN_CONFIRMED", HOST));

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
                new StubQueryPort(List.of(action(1L, "HUMAN_CONFIRMED", HOST))), dispatch,
                gaps(0), new MeetingAccessGuard((companyId, meetingId) -> true),
                meetingId -> Optional.empty(), new RecordingApplyReviewDecisionUseCase(),
                (companyId, meetingId, confirmedAt) -> { }, fixedClock());

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
                action(1L, "HUMAN_CONFIRMED", HOST),
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

    @Test
    @DisplayName("담당자 없는 TEAM 액션도 확정되면 나간다 — TEAM은 담당자 개념이 없다(2026-08-11, CodeRabbit 지적으로 발견한 기존 버그)")
    void 담당자_없는_TEAM_액션도_확정되면_내보낸다() {
        RecordingDispatchPort dispatch = new RecordingDispatchPort();
        List<ActionReviewQueryPort.ReviewAction> actions = List.of(teamAction(1L, "HUMAN_CONFIRMED"));

        DistributionConfirmed confirmed = service(actions, dispatch, 0).confirm(command(HOST, false));

        assertThat(dispatch.dispatched).containsExactly(1L);
        assertThat(confirmed.skipped()).isEmpty();
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
                new RecordingApplyReviewDecisionUseCase(),
                (companyId, meetingId, confirmedAt) -> { },
                fixedClock());
    }

    /* RVW-05가 D 회의에 전달한 최초 확정 정보를 기록하는 Port 대역이다. */
    private static final class RecordingMeetingActionConfirmationPort implements MeetingActionConfirmationPort {

        /* 실제 전달된 테넌트·회의·시각을 테스트 검증을 위해 보관한다. */
        private Long companyId;
        private Long meetingId;
        private LocalDateTime confirmedAt;

        /* 확정 호출의 실제 인자를 손실 없이 기록한다. */
        @Override
        public void confirmActions(Long companyId, Long meetingId, LocalDateTime confirmedAt) {
            /* 한 번의 RVW-05 호출이 전달한 값을 그대로 보관한다. */
            this.companyId = companyId;
            this.meetingId = meetingId;
            this.confirmedAt = confirmedAt;
        }
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
        return action(actionId, reviewStatus, assignee, false);
    }

    private ActionReviewQueryPort.ReviewAction action(long actionId, String reviewStatus, Long assignee,
                                                      Boolean nearMatched) {
        return new ActionReviewQueryPort.ReviewAction(
                actionId, ActionType.PERSONAL, assignee, assignee != null ? "김서준" : null, null,
                "로드맵 초안 작성", null, LocalDate.of(2026, 8, 8), false,
                nearMatched, "로드맵", false, reviewStatus, null, null, null, null);
    }

    /* TEAM 액션은 담당자 개념이 없다 — assigneeMemberId가 항상 null인 게 정상이다. */
    private ActionReviewQueryPort.ReviewAction teamAction(long actionId, String reviewStatus) {
        return new ActionReviewQueryPort.ReviewAction(
                actionId, ActionType.TEAM, null, null, null,
                "팀 회고 준비", null, LocalDate.of(2026, 8, 8), false,
                // 근접 매칭 여부는 이 테스트의 판정(확정 게이트)에 쓰이지 않는다 — false 로 둔다.
                false, "로드맵", false, reviewStatus, null, null, null, null);
    }

    /*
     * 2026-08-11 — 이제 findByMeeting(reviewStatus)이 실제로 필터링해야 한다
     * (confirmReviewablePendingActions가 PENDING만 따로 조회한 뒤 암묵 CONFIRM 처리한다).
     * markConfirmed는 그 암묵 확정을 흉내 내 이 스텁의 내부 상태를 갱신한다 — 실제 어댑터가
     * DB를 갱신하는 것과 같은 자리다.
     */
    private static final class StubQueryPort implements ActionReviewQueryPort {

        private final Map<Long, ReviewAction> actionsById = new LinkedHashMap<>();

        private StubQueryPort(List<ActionReviewQueryPort.ReviewAction> actions) {
            for (ReviewAction action : actions) {
                actionsById.put(action.actionId(), action);
            }
        }

        void markConfirmed(long actionId) {
            ReviewAction current = actionsById.get(actionId);
            actionsById.put(actionId, new ReviewAction(
                    current.actionId(), current.actionType(), current.assigneeMemberId(),
                    current.assigneeName(), current.assigneeSource(), current.title(), current.detail(),
                    current.dueDate(), current.dueDateDefaulted(), current.assigneeNearMatched(),
                    current.topic(), current.manual(),
                    "HUMAN_CONFIRMED", current.rejectReason(), current.evidence(), current.signals(),
                    current.autoConfirmed()));
        }

        @Override
        public List<ReviewAction> findByMeeting(long companyId, long meetingId, String reviewStatus) {
            return actionsById.values().stream()
                    .filter(a -> reviewStatus == null || reviewStatus.equals(a.reviewStatus()))
                    .toList();
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

    /*
     * RVW-02 CONFIRM 경로를 흉내 낸다 — ConfirmDistributionService가 담당자 있는 PENDING을
     * 확정 버튼 한 번으로 암묵 처리할 때 부르는 자리다(2026-08-11 추가).
     */
    private static final class RecordingApplyReviewDecisionUseCase implements ApplyReviewDecisionUseCase {

        private final List<Long> confirmedActionIds = new ArrayList<>();
        private StubQueryPort query;

        void attach(StubQueryPort query) {
            this.query = query;
        }

        @Override
        public ReviewDecisionOutcome apply(ReviewDecisionCommand command) {
            assertThat(command.decision()).isEqualTo(ReviewDecision.CONFIRM);
            confirmedActionIds.add(command.actionId());
            if (query != null) {
                query.markConfirmed(command.actionId());
            }
            return new ReviewDecisionOutcome(command.actionId(), "HUMAN_CONFIRMED", true, false);
        }
    }

    /* 무엇을 내보냈는지가 검증 대상이다 — 특히 "안 내보낸 것"이다. */
    private static final class RecordingDispatchPort implements ActionDispatchPort {

        private final List<Long> dispatched = new ArrayList<>();
        private int calls;
        private LocalDateTime dispatchedAt;
        /* 이미 나가 있던 것으로 취급할 액션. 재확정 상황을 만드는 손잡이다. */
        private List<Long> alreadyDispatched = List.of();

        @Override
        public DispatchOutcome markDispatched(long companyId, List<Long> actionIds,
                                              LocalDateTime dispatchedAt) {
            calls++;
            this.dispatchedAt = dispatchedAt;
            List<Long> newly = actionIds.stream()
                    .filter(id -> !alreadyDispatched.contains(id))
                    .toList();
            dispatched.addAll(newly);
            return new DispatchOutcome(newly.size(), alreadyDispatched);
        }
    }

}
