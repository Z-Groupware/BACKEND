package com.module06.backend.action.infrastructure.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.ProjectReference;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * PR #422 리뷰 후속(이슈 #435) — projectDueDateOf 분기(projectId 유무)가 처음 실제 로직으로
 * 들어왔는데 이 어댑터 자체에 테스트가 없던 상태였다.
 */
@ExtendWith(MockitoExtension.class)
class ActionReviewApplyAdapterTest {

    private static final long COMPANY = 1L;
    private static final long ACTION_ID = 10L;
    private static final Long PROJECT_ID = 100L;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private ActionReferenceRepository actionReferenceRepository;

    private ActionReviewApplyAdapter adapter() {
        return new ActionReviewApplyAdapter(actionRepository, actionReferenceRepository);
    }

    @Test
    void appliesPlannedStartDateWithProjectDueDateWhenActionHasProject() {
        Action action = personalAction(PROJECT_ID);
        LocalDate dueDate = LocalDate.of(2026, 9, 1);
        LocalDate plannedStartDate = LocalDate.of(2026, 8, 20);
        LocalDate projectDueDate = LocalDate.of(2026, 9, 30);
        when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));
        when(actionReferenceRepository.findProjectReferences(List.of(PROJECT_ID)))
                .thenReturn(List.of(new ProjectReference(PROJECT_ID, projectDueDate, "TAG", "이름")));

        adapter().apply(COMPANY, ACTION_ID, null, dueDate, null, null, plannedStartDate, "HUMAN_CONFIRMED");

        // dueDate·plannedStartDate가 서로 안 바뀌었는지 — 포트 주석이 경고한 그 자리.
        assertThat(action.getDueDate()).isEqualTo(dueDate);
        assertThat(action.getPlannedStartDate()).isEqualTo(plannedStartDate);
        verify(actionRepository).save(action);
    }

    @Test
    void rejectsPlannedStartDateWhenActionHasNoProject() {
        Action action = personalAction(null);
        when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));

        // projectDueDateOf가 projectId==null이면 조회 없이 바로 null을 돌려주고,
        // Action.applyHumanReview가 그 null을 거절한다(도메인 쪽 검증, 여기선 위임 확인).
        assertThatThrownBy(() -> adapter().apply(
                COMPANY, ACTION_ID, null, null, null, null, LocalDate.of(2026, 8, 20), "HUMAN_CONFIRMED")
        ).isInstanceOf(IllegalArgumentException.class);

        verify(actionReferenceRepository, never()).findProjectReferences(any());
        verify(actionRepository, never()).save(any());
    }

    @Test
    void skipsProjectLookupWhenPlannedStartDateIsNull() {
        Action action = personalAction(PROJECT_ID);
        when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));

        adapter().apply(COMPANY, ACTION_ID, null, LocalDate.of(2026, 9, 1), null, null, null, "HUMAN_CONFIRMED");

        // 예정 시작일과 무관한 판정에 프로젝트 조회를 얹지 않는다(어댑터 주석의 그 이유).
        verify(actionReferenceRepository, never()).findProjectReferences(any());
        assertThat(action.getPlannedStartDate()).isNull();
        verify(actionRepository).save(action);
    }

    @Test
    void throwsNotFoundWhenActionBelongsToAnotherCompany() {
        Action action = personalAction(PROJECT_ID);
        when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> adapter().apply(
                999L, ACTION_ID, null, null, null, null, null, "HUMAN_CONFIRMED")
        ).isInstanceOf(BusinessException.class);

        verify(actionRepository, never()).save(any());
    }

    @Test
    void throwsNotFoundWhenActionDoesNotExist() {
        when(actionRepository.findById(ACTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter().apply(
                COMPANY, ACTION_ID, null, null, null, null, null, "HUMAN_CONFIRMED")
        ).isInstanceOf(BusinessException.class);
    }

    private Action personalAction(Long projectId) {
        return Action.reconstitute(
                ACTION_ID, COMPANY, projectId, null, null, null, 5L,
                ActionType.PERSONAL, "액션", "설명", false, null, null, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.PENDING, null, null, null, false,
                null, null, null
        );
    }
}
