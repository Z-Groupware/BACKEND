package com.module06.backend.action.infrastructure.persistence;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.module06.backend.action.application.port.MeetingActionQueryPort.MeetingUndispatchedActions;
import com.module06.backend.action.domain.model.ActionReviewStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    void existsActionDelegatesArgumentsAndReturnValue() {
        when(springDataActionRepository.existsByCompanyIdAndId(1L, 42L)).thenReturn(true);

        assertThat(adapter.existsAction(1L, 42L)).isTrue();
    }

    private SpringDataActionRepository.UndispatchedProjection projection(Long sourceMeetingId) {
        return () -> sourceMeetingId;
    }
}
