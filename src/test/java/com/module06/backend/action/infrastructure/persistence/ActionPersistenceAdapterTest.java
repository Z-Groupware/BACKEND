package com.module06.backend.action.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActionPersistenceAdapter")
class ActionPersistenceAdapterTest {

    @Mock
    private SpringDataActionRepository springDataActionRepository;

    @InjectMocks
    private ActionPersistenceAdapter adapter;

    @Test
    @DisplayName("findById maps entity to domain")
    void findByIdMapsEntityToDomain() {
        when(springDataActionRepository.findById(1L)).thenReturn(Optional.of(entity(1L, ActionType.PERSONAL)));

        Optional<Action> result = adapter.findById(1L);

        assertThat(result).isPresent().get()
                .satisfies(action -> assertThat(action.getActionType()).isEqualTo(ActionType.PERSONAL));
    }

    @Test
    @DisplayName("save maps domain to entity and back")
    void saveMapsDomainToEntityAndBack() {
        Action action = domain(2L, ActionType.PERSONAL);
        when(springDataActionRepository.save(org.mockito.ArgumentMatchers.any(ActionJpaEntity.class)))
                .thenReturn(entity(2L, ActionType.PERSONAL));

        Action saved = adapter.save(action);

        assertThat(saved.getId()).isEqualTo(2L);
        verify(springDataActionRepository).save(org.mockito.ArgumentMatchers.argThat(entity ->
                entity.getId().equals(2L)
                        && entity.getActionType() == ActionType.PERSONAL
                        && entity.getAssigneeMemberId().equals(10L)));
    }

    @Test
    @DisplayName("findPersonalByAssignee with excludeDone delegates to status-not-DONE query")
    void findPersonalByAssigneeExcludingDoneDelegates() {
        when(springDataActionRepository.findAllByAssigneeMemberIdAndActionTypeAndStatusNotOrderByDueDateAscIdAsc(
                10L, ActionType.PERSONAL, ActionStatus.DONE))
                .thenReturn(List.of(entity(3L, ActionType.PERSONAL)));

        List<Action> result = adapter.findPersonalByAssignee(10L, true);

        assertThat(result).extracting(Action::getId).containsExactly(3L);
        verify(springDataActionRepository)
                .findAllByAssigneeMemberIdAndActionTypeAndStatusNotOrderByDueDateAscIdAsc(
                        10L, ActionType.PERSONAL, ActionStatus.DONE);
    }

    @Test
    @DisplayName("findPersonalByAssignee without excludeDone delegates to all personal query")
    void findPersonalByAssigneeIncludingDoneDelegates() {
        when(springDataActionRepository.findAllByAssigneeMemberIdAndActionTypeOrderByDueDateAscIdAsc(
                10L, ActionType.PERSONAL))
                .thenReturn(List.of(entity(4L, ActionType.PERSONAL)));

        List<Action> result = adapter.findPersonalByAssignee(10L, false);

        assertThat(result).extracting(Action::getId).containsExactly(4L);
        verify(springDataActionRepository)
                .findAllByAssigneeMemberIdAndActionTypeOrderByDueDateAscIdAsc(10L, ActionType.PERSONAL);
    }

    @Test
    @DisplayName("findAllPersonalByAssignee delegates to all personal query")
    void findAllPersonalByAssigneeDelegates() {
        when(springDataActionRepository.findAllByAssigneeMemberIdAndActionTypeOrderByDueDateAscIdAsc(
                10L, ActionType.PERSONAL))
                .thenReturn(List.of(entity(5L, ActionType.PERSONAL)));

        List<Action> result = adapter.findAllPersonalByAssignee(10L);

        assertThat(result).extracting(Action::getId).containsExactly(5L);
        verify(springDataActionRepository)
                .findAllByAssigneeMemberIdAndActionTypeOrderByDueDateAscIdAsc(10L, ActionType.PERSONAL);
    }

    @Test
    @DisplayName("findParentTeamActionsByAssignee collects parent ids then fetches TEAM parents (no @Query)")
    void findParentTeamActionsByAssigneeDelegates() {
        // entity(_, PERSONAL)의 parentActionId=500L → parentIds=[500L] → TEAM 부모 조회
        when(springDataActionRepository.findAllByAssigneeMemberIdAndActionTypeOrderByDueDateAscIdAsc(
                10L, ActionType.PERSONAL))
                .thenReturn(List.of(entity(3L, ActionType.PERSONAL)));
        when(springDataActionRepository.findAllByIdInAndActionTypeOrderByIdAsc(
                List.of(500L), ActionType.TEAM))
                .thenReturn(List.of(entity(6L, ActionType.TEAM)));

        List<Action> result = adapter.findParentTeamActionsByAssignee(10L);

        assertThat(result).extracting(Action::getId).containsExactly(6L);
        assertThat(result.get(0).getActionType()).isEqualTo(ActionType.TEAM);
        verify(springDataActionRepository).findAllByIdInAndActionTypeOrderByIdAsc(
                List.of(500L), ActionType.TEAM);
    }

    private Action domain(Long id, ActionType actionType) {
        return new Action(
                id,
                1L,
                100L,
                actionType == ActionType.PERSONAL ? 500L : null,
                300L,
                actionType == ActionType.TEAM ? 700L : null,
                actionType == ActionType.PERSONAL ? 10L : null,
                actionType,
                "Action " + id,
                "Content " + id,
                ActionStatus.TODO,
                LocalDate.of(2026, 8, 20),
                null,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 10, 0)
        );
    }

    private ActionJpaEntity entity(Long id, ActionType actionType) {
        return new ActionJpaEntity(
                id,
                1L,
                100L,
                actionType == ActionType.PERSONAL ? 500L : null,
                300L,
                actionType == ActionType.TEAM ? 700L : null,
                actionType == ActionType.PERSONAL ? 10L : null,
                actionType,
                "Action " + id,
                "Content " + id,
                ActionStatus.TODO,
                LocalDate.of(2026, 8, 20),
                null,
                LocalDateTime.of(2026, 8, 1, 10, 0),
                LocalDateTime.of(2026, 8, 1, 10, 0)
        );
    }
}
