package com.module06.backend.action.infrastructure.adapter;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.infrastructure.persistence.ActionMeetingReferenceRepository;
import com.module06.backend.action.infrastructure.persistence.SpringDataProjectReferenceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActionReassignAdapterTest {

    private static final Long ACTION_ID = 10L;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private SpringDataProjectReferenceRepository springDataProjectReferenceRepository;

    @Mock
    private ActionMeetingReferenceRepository actionMeetingReferenceRepository;

    private ActionReassignAdapter adapter() {
        return new ActionReassignAdapter(actionRepository, springDataProjectReferenceRepository, actionMeetingReferenceRepository);
    }

    @Test
    void reassignReadsThroughLockedFinderNotPlainFindById() {
        Action action = personalAction(5L);
        when(actionRepository.findByIdForUpdate(ACTION_ID)).thenReturn(Optional.of(action));

        adapter().reassign(ACTION_ID, 5L, 7L);

        verify(actionRepository, never()).findById(any());
        verify(actionRepository).save(action);
        assertThat(action.getAssigneeMemberId()).isEqualTo(7L);
    }

    @Test
    void reassignThrowsWhenCurrentAssigneeAlreadyChanged() {
        // 락으로 읽은 시점엔 이미 다른 요청이 담당자를 바꿔놓은 상태 — 그대로 덮어쓰지 않고 막는다.
        Action action = personalAction(999L);
        when(actionRepository.findByIdForUpdate(ACTION_ID)).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> adapter().reassign(ACTION_ID, 5L, 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("담당자가 일치하지 않는");

        verify(actionRepository, never()).save(any());
    }

    @Test
    void reassignThrowsWhenActionDoesNotExist() {
        when(actionRepository.findByIdForUpdate(ACTION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter().reassign(ACTION_ID, 5L, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 액션입니다");
    }

    @Test
    void rollbackReassignmentDelegatesToReassignSymmetrically() {
        Action action = personalAction(7L);
        when(actionRepository.findByIdForUpdate(ACTION_ID)).thenReturn(Optional.of(action));

        adapter().rollbackReassignment(ACTION_ID, 7L, 5L);

        verify(actionRepository).save(action);
        assertThat(action.getAssigneeMemberId()).isEqualTo(5L);
    }

    @Test
    void rollbackReassignmentThrowsWhenCurrentAssigneeAlreadyChanged() {
        Action action = personalAction(999L);
        when(actionRepository.findByIdForUpdate(ACTION_ID)).thenReturn(Optional.of(action));

        assertThatThrownBy(() -> adapter().rollbackReassignment(ACTION_ID, 7L, 5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("담당자가 일치하지 않는");

        verify(actionRepository, never()).save(any());
    }

    private Action personalAction(Long assigneeMemberId) {
        return Action.reconstitute(
                ACTION_ID, 1L, 100L, null, null, null, assigneeMemberId,
                ActionType.PERSONAL, "액션", "설명", false, null, null, LocalDate.of(2026, 8, 20), false,
                ActionReviewStatus.HUMAN_CONFIRMED, null, null, null, false,
                null, null, null
        );
    }
}
