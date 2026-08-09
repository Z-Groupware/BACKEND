package com.module06.backend.action.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.action.application.port.MeetingActionQueryPort;
import com.module06.backend.action.application.port.MeetingActionQueryPort.MeetingUndispatchedActions;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;

import static org.assertj.core.api.Assertions.assertThat;

/* comment.
    마이페이지 확정 대기 목록(MeetingActionQueryPort) 실 SQL 정합성 테스트. 판정식
    (company_id + source_meeting_id IN + dispatched_at IS NULL + review_status <> REJECTED)이
    실제 DB 위에서 맞게 동작하는지 확인한다 — 프로젝션·파생쿼리라 가짜 리포지터리로는
    검증할 수 없다.
*/
@SpringBootTest
@Transactional
class ActionPersistenceAdapterMeetingQueryTest {

    private static final Long COMPANY = 1L;
    private static final Long OTHER_COMPANY = 2L;
    private static final Long MEETING = 900L;

    @Autowired
    private MeetingActionQueryPort meetingActionQueryPort;

    @Autowired
    private SpringDataActionRepository springDataActionRepository;

    @Test
    void includesOnlyOwnCompanyAmongMixedCompanyRows() {
        saveAction(COMPANY, MEETING, ActionReviewStatus.PENDING, null);
        saveAction(OTHER_COMPANY, MEETING, ActionReviewStatus.PENDING, null);

        List<MeetingUndispatchedActions> result =
                meetingActionQueryPort.findMeetingsWithUndispatchedActions(COMPANY, List.of(MEETING));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceMeetingId()).isEqualTo(MEETING);
        assertThat(result.get(0).undispatchedCount()).isEqualTo(1L);
    }

    @Test
    void includesHumanConfirmedActionThatIsNotDispatchedYet() {
        // 이번 설계의 핵심 — review_status만 보면 놓치는 케이스다.
        saveAction(COMPANY, MEETING, ActionReviewStatus.HUMAN_CONFIRMED, null);

        List<MeetingUndispatchedActions> result =
                meetingActionQueryPort.findMeetingsWithUndispatchedActions(COMPANY, List.of(MEETING));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).undispatchedCount()).isEqualTo(1L);
    }

    @Test
    void excludesMeetingWhereEveryActionIsAlreadyDispatched() {
        saveAction(COMPANY, MEETING, ActionReviewStatus.HUMAN_CONFIRMED, java.time.LocalDateTime.now());

        List<MeetingUndispatchedActions> result =
                meetingActionQueryPort.findMeetingsWithUndispatchedActions(COMPANY, List.of(MEETING));

        assertThat(result).isEmpty();
    }

    @Test
    void excludesMeetingWithOnlyRejectedActions() {
        // REJECTED는 분배에서 skip되어 dispatched_at이 영원히 NULL — 빼지 않으면 무한 잔류한다.
        saveAction(COMPANY, MEETING, ActionReviewStatus.REJECTED, null);

        List<MeetingUndispatchedActions> result =
                meetingActionQueryPort.findMeetingsWithUndispatchedActions(COMPANY, List.of(MEETING));

        assertThat(result).isEmpty();
    }

    @Test
    void ignoresActionsWithoutSourceMeetingId() {
        ActionJpaEntity manual = baseEntity(COMPANY, null, ActionReviewStatus.PENDING).build();
        springDataActionRepository.save(manual);

        List<MeetingUndispatchedActions> result =
                meetingActionQueryPort.findMeetingsWithUndispatchedActions(COMPANY, List.of(MEETING));

        assertThat(result).isEmpty();
    }

    @Test
    void includesUndispatchedActionWithoutAssignee() {
        // 담당자 없는 PERSONAL 액션도 분배 대상 판정에서 빠지지 않는다(2026-08-07 합의).
        ActionJpaEntity withoutAssignee = baseEntity(COMPANY, MEETING, ActionReviewStatus.PENDING)
                .assigneeMemberId(null)
                .build();
        springDataActionRepository.save(withoutAssignee);

        List<MeetingUndispatchedActions> result =
                meetingActionQueryPort.findMeetingsWithUndispatchedActions(COMPANY, List.of(MEETING));

        assertThat(result).hasSize(1);
    }

    private void saveAction(Long companyId, Long meetingId, ActionReviewStatus reviewStatus,
                             java.time.LocalDateTime dispatchedAt) {
        ActionJpaEntity entity = baseEntity(companyId, meetingId, reviewStatus).build();
        if (dispatchedAt != null) {
            entity.markDispatched(dispatchedAt);
        }
        springDataActionRepository.save(entity);
    }

    private ActionJpaEntity.ActionJpaEntityBuilder baseEntity(
            Long companyId, Long meetingId, ActionReviewStatus reviewStatus) {
        return ActionJpaEntity.builder()
                .companyId(companyId)
                .projectId(1L)
                .sourceMeetingId(meetingId)
                .assigneeMemberId(10L)
                .actionType(ActionType.PERSONAL)
                .title("제목")
                .status(ActionStatus.TODO)
                .isDone(false)
                .dueDate(LocalDate.of(2026, 8, 20))
                .dueDateDefaulted(false)
                .reviewStatus(reviewStatus)
                .isManual(false);
    }
}
