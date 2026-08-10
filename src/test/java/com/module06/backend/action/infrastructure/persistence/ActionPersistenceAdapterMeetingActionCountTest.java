package com.module06.backend.action.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.action.application.port.MeetingActionQueryPort;
import com.module06.backend.action.application.port.MeetingActionQueryPort.MeetingActionCount;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;

import static org.assertj.core.api.Assertions.assertThat;

/* comment.
    회의 목록 카드 "액션 N건" 표시(MeetingActionQueryPort.countActionsByMeetings) 실 SQL
    정합성 테스트. ActionPersistenceAdapterMeetingQueryTest와 자매 클래스 — 저쪽은 분배·검토
    조건이 걸린 판정식, 이쪽은 조건 없는 전체 건수라는 차이만 검증한다.
*/
@SpringBootTest
@Transactional
class ActionPersistenceAdapterMeetingActionCountTest {

    private static final Long COMPANY = 1L;
    private static final Long OTHER_COMPANY = 2L;
    private static final Long MEETING = 900L;
    private static final Long OTHER_MEETING = 901L;

    @Autowired
    private MeetingActionQueryPort meetingActionQueryPort;

    @Autowired
    private SpringDataActionRepository springDataActionRepository;

    @Test
    void countsAllActionsRegardlessOfDispatchOrReviewStatus() {
        // findMeetingsWithUndispatchedActions라면 REJECTED·이미 분배된 건은 빠지지만,
        // 여기는 전체 건수라 셋 다 잡혀야 한다.
        saveDispatchedAction(COMPANY, MEETING, ActionReviewStatus.HUMAN_CONFIRMED);
        saveAction(COMPANY, MEETING, ActionReviewStatus.REJECTED);
        saveAction(COMPANY, MEETING, ActionReviewStatus.PENDING);

        List<MeetingActionCount> result =
                meetingActionQueryPort.countActionsByMeetings(COMPANY, List.of(MEETING));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceMeetingId()).isEqualTo(MEETING);
        assertThat(result.get(0).actionCount()).isEqualTo(3L);
    }

    @Test
    void includesOnlyOwnCompanyAmongMixedCompanyRows() {
        saveAction(COMPANY, MEETING, ActionReviewStatus.PENDING);
        saveAction(OTHER_COMPANY, MEETING, ActionReviewStatus.PENDING);

        List<MeetingActionCount> result =
                meetingActionQueryPort.countActionsByMeetings(COMPANY, List.of(MEETING));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).actionCount()).isEqualTo(1L);
    }

    @Test
    void separatesCountsPerMeeting() {
        saveAction(COMPANY, MEETING, ActionReviewStatus.PENDING);
        saveAction(COMPANY, MEETING, ActionReviewStatus.PENDING);
        saveAction(COMPANY, OTHER_MEETING, ActionReviewStatus.PENDING);

        List<MeetingActionCount> result =
                meetingActionQueryPort.countActionsByMeetings(COMPANY, List.of(MEETING, OTHER_MEETING));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MeetingActionCount::sourceMeetingId)
                .containsExactlyInAnyOrder(MEETING, OTHER_MEETING);
        assertThat(result).filteredOn(r -> r.sourceMeetingId().equals(MEETING))
                .extracting(MeetingActionCount::actionCount).containsExactly(2L);
    }

    @Test
    void excludesMeetingWithNoActionsInstead_ofPaddingZero() {
        // 0건 회의는 키 자체가 안 생긴다 — 계약에 문서화된 대로, 채우는 건 호출자(D) 몫이다.
        List<MeetingActionCount> result =
                meetingActionQueryPort.countActionsByMeetings(COMPANY, List.of(MEETING));

        assertThat(result).isEmpty();
    }

    @Test
    void ignoresActionsWithoutSourceMeetingId() {
        ActionJpaEntity manual = baseEntity(COMPANY, null, ActionReviewStatus.PENDING).build();
        springDataActionRepository.save(manual);

        List<MeetingActionCount> result =
                meetingActionQueryPort.countActionsByMeetings(COMPANY, List.of(MEETING));

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyListWhenSourceMeetingIdsIsEmpty() {
        assertThat(meetingActionQueryPort.countActionsByMeetings(COMPANY, List.of())).isEmpty();
    }

    private void saveAction(Long companyId, Long meetingId, ActionReviewStatus reviewStatus) {
        springDataActionRepository.save(baseEntity(companyId, meetingId, reviewStatus).build());
    }

    private void saveDispatchedAction(Long companyId, Long meetingId, ActionReviewStatus reviewStatus) {
        ActionJpaEntity entity = baseEntity(companyId, meetingId, reviewStatus).build();
        entity.markDispatched(java.time.LocalDateTime.now());
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
