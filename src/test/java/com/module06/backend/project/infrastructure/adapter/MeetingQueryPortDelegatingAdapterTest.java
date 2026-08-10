package com.module06.backend.project.infrastructure.adapter;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingQueryPortDelegatingAdapterTest {

    @Mock
    private com.module06.backend.meeting.application.port.in.MeetingQueryPort meetingQueryPort;

    private MeetingQueryPortDelegatingAdapter adapter;

    @Test
    void returnsEmptyWithoutCallingMeetingPortWhenProjectIdsIsEmpty() {
        adapter = new MeetingQueryPortDelegatingAdapter(meetingQueryPort);

        Map<Long, Long> result = adapter.countMeetingsByProjectIds(1L, List.of());

        assertThat(result).isEmpty();
        verify(meetingQueryPort, never()).countMeetingsByProjectIds(any(), any());
    }

    @Test
    void delegatesToMeetingPortAndReturnsItsResult() {
        adapter = new MeetingQueryPortDelegatingAdapter(meetingQueryPort);
        when(meetingQueryPort.countMeetingsByProjectIds(1L, List.of(10L, 20L)))
                .thenReturn(Map.of(10L, 3L, 20L, 0L));

        Map<Long, Long> result = adapter.countMeetingsByProjectIds(1L, List.of(10L, 20L));

        assertThat(result).containsEntry(10L, 3L).containsEntry(20L, 0L);
    }
}
