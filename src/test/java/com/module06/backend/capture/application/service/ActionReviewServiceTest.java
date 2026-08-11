package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.capture.application.port.out.ActionReviewQueryPort;
import com.module06.backend.capture.application.port.out.MeetingAccessPort;
import com.module06.backend.capture.application.result.ActionReview;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.GateSignals;
import com.module06.backend.capture.domain.model.RejectReason;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RVW-01 검토 조회 — 파이프라인 산출물을 사람이 처음 보는 자리.
 *
 * <p>여기서 지켜야 하는 것은 <b>아무것도 화면에서 사라지지 않는 것</b>이다. 담당자가 없든
 * 근거가 없든 게이트를 안 지났든, 검토 대상에서 빠지면 사람이 볼 기회 자체가 없어진다.
 */
class ActionReviewServiceTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final long ALICE = 11L;
    private static final long BOB = 12L;

    @Test
    @DisplayName("담당자별로 묶어 돌려준다 — 화면의 1차 축이 사람이다")
    void 담당자별로_묶는다() {
        FakeQueryPort port = new FakeQueryPort(List.of(
                action(1L, ALICE, "김서준", true),
                action(2L, ALICE, "김서준", true),
                action(3L, BOB, "이수아", true)));

        ActionReview review = service(port).getReview(COMPANY, MEETING, null);

        assertThat(review.actionsByPerson()).hasSize(2);
        assertThat(review.actionsByPerson().get(0).memberId()).isEqualTo(ALICE);
        assertThat(review.actionsByPerson().get(0).actions()).hasSize(2);
        assertThat(review.actionsByPerson().get(1).memberId()).isEqualTo(BOB);
    }

    @Test
    @DisplayName("담당자가 없는 액션도 묶음으로 남긴다 — 담당자 미정이야말로 사람이 봐야 한다")
    void 담당자_미정도_화면에_남는다() {
        FakeQueryPort port = new FakeQueryPort(List.of(action(1L, null, null, false)));

        ActionReview review = service(port).getReview(COMPANY, MEETING, null);

        assertThat(review.actionsByPerson()).hasSize(1);
        assertThat(review.actionsByPerson().get(0).memberId()).isNull();
        assertThat(review.actionsByPerson().get(0).actions()).hasSize(1);
    }

    @Test
    @DisplayName("자동확정되지 않은 것을 검토 대상으로 센다")
    void 검토_대상을_센다() {
        FakeQueryPort port = new FakeQueryPort(List.of(
                action(1L, ALICE, "김서준", true),
                action(2L, ALICE, "김서준", false),
                action(3L, BOB, "이수아", false)));

        ActionReview review = service(port).getReview(COMPANY, MEETING, null);

        assertThat(review.needsReview().count()).isEqualTo(2);
        assertThat(review.needsReview().actionIds()).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("게이트를 안 지난 액션(수동 추가)도 검토 대상이다")
    void 게이트를_안_지난_것도_검토_대상이다() {
        // 게이트가 떨어뜨린 것과 게이트를 아예 안 지난 것은 원인이 다르지만,
        // 사람이 봐야 하는 것은 같다. 명세도 화면을 둘로만 나눈다.
        FakeQueryPort port = new FakeQueryPort(List.of(manualAction(9L)));

        ActionReview review = service(port).getReview(COMPANY, MEETING, null);

        assertThat(review.needsReview().actionIds()).containsExactly(9L);
        assertThat(review.actionsByPerson().get(0).actions().get(0).signals()).isNull();
    }

    @Test
    @DisplayName("분배 전이면 dispatchedAt 은 null 이다 — 자동 확정 건도 아직 아무 데도 없다")
    void 분배_전에는_dispatchedAt이_없다() {
        // 자동 확정 건도 분배 전까지는 아무 데도 가 있지 않다(명세 RVW-01).
        FakeQueryPort port = new FakeQueryPort(List.of(action(1L, ALICE, "김서준", true)));

        assertThat(service(port).getReview(COMPANY, MEETING, null).dispatchedAt()).isNull();
    }

    @Test
    @DisplayName("분배 확정된 회의는 그 시각을 내려준다 — 화면이 「확정됨」을 이 값으로 그린다")
    void 분배_뒤에는_dispatchedAt이_있다() {
        FakeQueryPort port = new FakeQueryPort(List.of(action(1L, ALICE, "김서준", true)));
        port.dispatchedAt = java.time.LocalDateTime.of(2026, 8, 7, 15, 31, 2);

        assertThat(service(port).getReview(COMPANY, MEETING, null).dispatchedAt())
                .isEqualTo(java.time.LocalDateTime.of(2026, 8, 7, 15, 31, 2));
    }

    @Test
    @DisplayName("반려 사유를 그대로 내려준다 — 상태만 주면 다음 사람이 이유를 몰라 다시 묻는다")
    void 반려_사유를_내려준다() {
        FakeQueryPort port = new FakeQueryPort(List.of(rejectedAction(1L)));

        ActionReview review = service(port).getReview(COMPANY, MEETING, null);

        // 화면의 「반려됨 · 이미 있는 것과 중복」에서 뒷부분이 이 값이다.
        assertThat(review.actionsByPerson().get(0).actions().get(0).rejectReason())
                .isEqualTo(RejectReason.DUPLICATE);
        /*
         * 반려됐지만 자동확정된 건이라 검토 대상 카운트에는 들어가지 않는다. 화면도 이 카드를
         * 「AI 확신도 높음」 묶음에 남긴 채 반려 라벨만 붙인다 — 반려가 카드를 지우지 않는다.
         */
        assertThat(review.needsReview().count()).isZero();
    }

    @Test
    @DisplayName("기한이 프로젝트 마감일로 채워진 것인지 함께 내려준다")
    void 기본값으로_채운_기한을_표시한다() {
        /*
         * 회의에서 기한을 말하지 않은 액션도 action.due_date 가 NOT NULL 이라 날짜가 채워진다.
         * 이 값이 없으면 화면에서 둘이 똑같이 보이고, 사람은 AI 가 그 날짜를 정했다고 읽는다.
         */
        FakeQueryPort port = new FakeQueryPort(List.of(defaultedDueAction(1L)));

        ActionReview review = service(port).getReview(COMPANY, MEETING, null);

        assertThat(review.actionsByPerson().get(0).actions().get(0).dueDateDefaulted()).isTrue();
    }

    @Test
    @DisplayName("reviewStatus 필터를 그대로 조회에 넘긴다")
    void 필터를_그대로_넘긴다() {
        FakeQueryPort port = new FakeQueryPort(List.of());

        service(port).getReview(COMPANY, MEETING, "PENDING");

        assertThat(port.lastReviewStatus).isEqualTo("PENDING");
        // 회사 스코프도 조회 조건으로 내려가야 한다 — 두 번째 방어선이다.
        assertThat(port.lastCompanyId).isEqualTo(COMPANY);
    }

    @Test
    @DisplayName("다른 회사 회의는 관문에서 막는다 — 조회 자체를 하지 않는다")
    void 다른_회사_회의는_막는다() {
        // 로그인한 사원이 남의 회사 회의 id 를 넣는 것은 @PreAuthorize 가 보지 않는다(#100).
        FakeQueryPort port = new FakeQueryPort(List.of());
        ActionReviewService service = new ActionReviewService(
                port, new MeetingAccessGuard((companyId, meetingId) -> false));

        assertThatThrownBy(() -> service.getReview(COMPANY, MEETING, null))
                .isInstanceOf(BusinessException.class);
        assertThat(port.called).isFalse();
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private ActionReviewService service(FakeQueryPort port) {
        return new ActionReviewService(port, new MeetingAccessGuard((companyId, meetingId) -> true));
    }

    private static ActionReviewQueryPort.ReviewAction action(long actionId, Long assignee,
                                                             String assigneeName, boolean autoConfirmed) {
        return new ActionReviewQueryPort.ReviewAction(
                actionId, ActionType.PERSONAL, assignee, assigneeName, AssigneeSource.EXPLICIT_CALL,
                "로드맵 초안 작성", null, LocalDate.of(2026, 8, 7),
                // 회의에서 나온 기한이다 — 프로젝트 마감일로 채운 것이 아니다.
                false,
                "제품 로드맵", false, "PENDING",
                // 아직 판정을 받지 않았으므로 사유가 없다.
                null,
                new ActionReviewQueryPort.Evidence(8812L, "박대표", "서준님이 정리해주세요.", 1_284_000),
                /*
                 * 신호 넷과 autoConfirmed 를 **따로 둔다.** 네 번째 신호는 viewsAgree 이고,
                 * 그것을 autoConfirmed 에 묶으면 "신호 넷은 통과했는데 L6 모순 때문에 걸린 건"을
                 * 이 픽스처로 만들 수 없다 — 게이트가 실제로 그 조합을 만든다.
                 */
                new GateSignals(true, assignee != null, true, true),
                autoConfirmed);
    }

    /* 사람이 직접 추가한 액션(RVW-03) — 게이트도 근거도 없다. */
    private static ActionReviewQueryPort.ReviewAction manualAction(long actionId) {
        return new ActionReviewQueryPort.ReviewAction(
                actionId, ActionType.PERSONAL, ALICE, "김서준", null, "직접 추가한 일", null, null,
                false, null, true, "HUMAN_CONFIRMED", null, null, null, null);
    }

    /* 사람이 반려한 액션 — 화면의 「반려됨 · 이미 있는 것과 중복」이 이 모양이다. */
    private static ActionReviewQueryPort.ReviewAction rejectedAction(long actionId) {
        return new ActionReviewQueryPort.ReviewAction(
                actionId, ActionType.PERSONAL, ALICE, "김서준", AssigneeSource.EXPLICIT_CALL,
                "온보딩 플로우 검토", null,
                LocalDate.of(2026, 8, 7), false, "온보딩", false, "REJECTED",
                RejectReason.DUPLICATE,
                new ActionReviewQueryPort.Evidence(8812L, "박대표", "먼저 검토합시다.", 1_122_000),
                new GateSignals(true, true, true, true), true);
    }

    /* 회의에서 기한을 말하지 않아 프로젝트 마감일로 채워진 액션. */
    private static ActionReviewQueryPort.ReviewAction defaultedDueAction(long actionId) {
        return new ActionReviewQueryPort.ReviewAction(
                actionId, ActionType.PERSONAL, ALICE, "김서준", AssigneeSource.EXPLICIT_CALL,
                "API 문서 최신화", null,
                LocalDate.of(2026, 8, 31), true, "인증 개편", false, "PENDING", null,
                new ActionReviewQueryPort.Evidence(8813L, "박도현", "API 문서에도 반영이 필요합니다.", 24_000),
                new GateSignals(true, true, true, true), true);
    }

    private static final class FakeQueryPort implements ActionReviewQueryPort {

        private final List<ReviewAction> actions;
        private boolean called;
        private Long lastCompanyId;
        private String lastReviewStatus;

        private FakeQueryPort(List<ReviewAction> actions) {
            this.actions = new ArrayList<>(actions);
        }

        /* RVW-02 가 쓰는 단건 조회다. 이 테스트는 목록만 보므로 부르지 않는다. */
        @Override
        public java.util.Optional<ReviewTarget> findOne(long companyId, long meetingId, long actionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReviewAction> findByMeeting(long companyId, long meetingId, String reviewStatus) {
            called = true;
            lastCompanyId = companyId;
            lastReviewStatus = reviewStatus;
            return actions;
        }

        /* 분배 확정 시각. 기본은 비어 있다 — 확정 전 회의가 검토 화면의 정상 상태다. */
        private java.time.LocalDateTime dispatchedAt;

        @Override
        public Optional<java.time.LocalDateTime> dispatchedAtOf(long companyId, long meetingId) {
            return Optional.ofNullable(dispatchedAt);
        }
    }
}
