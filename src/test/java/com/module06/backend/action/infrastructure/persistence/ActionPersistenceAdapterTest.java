package com.module06.backend.action.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.application.port.ActionQueryPort.ProjectActionCount;
import com.module06.backend.action.application.port.MeetingActionQueryPort.MeetingUndispatchedActions;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/* comment.
    MeetingActionQueryPort 구현부의 순수 로직(가드·청킹·집계)만 가짜 리포지터리로 검증한다.
    실제 SQL 조건(review_status·dispatched_at 판정)은 ActionPersistenceAdapterMeetingQueryTest가
    실 DB 위에서 확인한다 — 여긴 그 앞뒤 로직만 본다.
*/
@ExtendWith(MockitoExtension.class)
class ActionPersistenceAdapterTest {

    @Mock
    private SpringDataActionRepository springDataActionRepository;

    @Mock
    private SpringDataProjectReferenceRepository springDataProjectReferenceRepository;

    @Mock
    private SpringDataActionTeamReferenceRepository springDataActionTeamReferenceRepository;

    private ActionPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ActionPersistenceAdapter(
                springDataActionRepository, springDataProjectReferenceRepository, springDataActionTeamReferenceRepository);
    }

    @Test
    void returnsEmptyWithoutQueryingWhenSourceMeetingIdsIsEmpty() {
        List<MeetingUndispatchedActions> result =
                adapter.findMeetingsWithUndispatchedActions(1L, List.of());

        assertThat(result).isEmpty();
        verify(springDataActionRepository, never())
                .findAllByCompanyIdAndSourceMeetingIdInAndDispatchedAtIsNullAndReviewStatusNot(any(), anyList(), any());
    }

    @Test
    void returnsEmptyWithoutQueryingWhenSourceMeetingIdsIsNull() {
        List<MeetingUndispatchedActions> result =
                adapter.findMeetingsWithUndispatchedActions(1L, null);

        assertThat(result).isEmpty();
        verify(springDataActionRepository, never())
                .findAllByCompanyIdAndSourceMeetingIdInAndDispatchedAtIsNullAndReviewStatusNot(any(), anyList(), any());
    }

    @Test
    void returnsEmptyWithoutQueryingWhenCompanyIdIsNull() {
        List<MeetingUndispatchedActions> result =
                adapter.findMeetingsWithUndispatchedActions(null, List.of(1L));

        assertThat(result).isEmpty();
        verify(springDataActionRepository, never())
                .findAllByCompanyIdAndSourceMeetingIdInAndDispatchedAtIsNullAndReviewStatusNot(any(), anyList(), any());
    }

    @Test
    void aggregatesMultipleRowsOfSameMeetingIntoOneCount() {
        when(springDataActionRepository.findAllByCompanyIdAndSourceMeetingIdInAndDispatchedAtIsNullAndReviewStatusNot(
                eq(1L), eq(List.of(500L)), eq(ActionReviewStatus.REJECTED)))
                .thenReturn(List.of(projection(500L), projection(500L), projection(500L)));

        List<MeetingUndispatchedActions> result =
                adapter.findMeetingsWithUndispatchedActions(1L, List.of(500L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceMeetingId()).isEqualTo(500L);
        assertThat(result.get(0).undispatchedCount()).isEqualTo(3L);
    }

    @Test
    void splitsRequestIntoChunksOf200AndSumsResults() {
        List<Long> meetingIds = IntStream.rangeClosed(1, 250).mapToObj(Long::valueOf).toList();
        List<Long> firstChunk = meetingIds.subList(0, 200);
        List<Long> secondChunk = meetingIds.subList(200, 250);

        when(springDataActionRepository.findAllByCompanyIdAndSourceMeetingIdInAndDispatchedAtIsNullAndReviewStatusNot(
                eq(1L), eq(firstChunk), eq(ActionReviewStatus.REJECTED)))
                .thenReturn(List.of(projection(1L)));
        when(springDataActionRepository.findAllByCompanyIdAndSourceMeetingIdInAndDispatchedAtIsNullAndReviewStatusNot(
                eq(1L), eq(secondChunk), eq(ActionReviewStatus.REJECTED)))
                .thenReturn(List.of(projection(201L)));

        List<MeetingUndispatchedActions> result = adapter.findMeetingsWithUndispatchedActions(1L, meetingIds);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(MeetingUndispatchedActions::sourceMeetingId)
                .containsExactlyInAnyOrder(1L, 201L);
    }

    @Test
    void deduplicatesMeetingIdsBeforeChunkingToAvoidDoubleCounting() {
        // 코드래빗 지적(PR #229): 중복 meetingId가 서로 다른 청크로 쪼개지면 같은 DB 행이
        // 두 청크의 쿼리에서 각각 잡혀 집계가 부풀려진다. 201개(중복 1건 포함)를 넣어도
        // distinct 후엔 200개 한 청크로 끝나야 한다 — 쿼리 1회, 집계 중복 없음.
        List<Long> uniqueIds = IntStream.rangeClosed(1, 200).mapToObj(Long::valueOf).toList();
        List<Long> idsWithDuplicate = new ArrayList<>(uniqueIds);
        idsWithDuplicate.add(1L);

        when(springDataActionRepository.findAllByCompanyIdAndSourceMeetingIdInAndDispatchedAtIsNullAndReviewStatusNot(
                eq(1L), eq(uniqueIds), eq(ActionReviewStatus.REJECTED)))
                .thenReturn(List.of(projection(1L)));

        List<MeetingUndispatchedActions> result = adapter.findMeetingsWithUndispatchedActions(1L, idsWithDuplicate);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).undispatchedCount()).isEqualTo(1L);
        verify(springDataActionRepository, times(1))
                .findAllByCompanyIdAndSourceMeetingIdInAndDispatchedAtIsNullAndReviewStatusNot(any(), anyList(), any());
    }

    @Test
    void existsActionDelegatesArgumentsAndReturnValue() {
        when(springDataActionRepository.existsByCompanyIdAndId(1L, 42L)).thenReturn(true);

        assertThat(adapter.existsAction(1L, 42L)).isTrue();
    }

    @Test
    void countActionsByProjectIdsReturnsEmptyWithoutQueryingWhenProjectIdsIsEmpty() {
        List<ProjectActionCount> result = adapter.countActionsByProjectIds(1L, List.of());

        assertThat(result).isEmpty();
        verify(springDataActionRepository, never())
                .findAllByActionTypeAndCompanyIdAndProjectIdIn(any(), any(), anyList());
    }

    @Test
    void countActionsByProjectIdsGroupsAndCountsDoneSeparately() {
        when(springDataActionRepository
                .findAllByActionTypeAndCompanyIdAndProjectIdIn(ActionType.PERSONAL, 1L, List.of(1L, 2L)))
                .thenReturn(List.of(
                        actionProjection(1L, ActionStatus.DONE),
                        actionProjection(1L, ActionStatus.DONE),
                        actionProjection(1L, ActionStatus.TODO),
                        actionProjection(2L, ActionStatus.IN_PROGRESS)
                ));

        List<ProjectActionCount> result = adapter.countActionsByProjectIds(1L, List.of(1L, 2L));

        assertThat(result).containsExactlyInAnyOrder(
                new ProjectActionCount(1L, 3, 2),
                new ProjectActionCount(2L, 1, 0));
    }

    // 팀 액션은 집계에서 빠진다(WORKFLOW §1). 스텁 데이터로는 그것을 못 보인다 — 리포지터리가
    // 이미 걸러서 돌려주므로 가짜 리포지터리에 TEAM 행을 넣어도 의미가 없다. 그래서 검증 대상은
    // 결과가 아니라 "어떤 인자로 부르는가" 하나다.
    @Test
    void countActionsByProjectIdsQueriesPersonalActionsOfOwnCompanyOnly() {
        when(springDataActionRepository
                .findAllByActionTypeAndCompanyIdAndProjectIdIn(ActionType.PERSONAL, 7L, List.of(1L)))
                .thenReturn(List.of(actionProjection(1L, ActionStatus.DONE)));

        adapter.countActionsByProjectIds(7L, List.of(1L));

        verify(springDataActionRepository)
                .findAllByActionTypeAndCompanyIdAndProjectIdIn(ActionType.PERSONAL, 7L, List.of(1L));
    }

    private SpringDataActionRepository.UndispatchedProjection projection(Long sourceMeetingId) {
        return () -> sourceMeetingId;
    }

    private SpringDataActionRepository.ProjectActionProjection actionProjection(Long projectId, ActionStatus status) {
        return new SpringDataActionRepository.ProjectActionProjection() {
            @Override
            public Long getProjectId() {
                return projectId;
            }

            @Override
            public ActionStatus getStatus() {
                return status;
            }
        };
    }
}
