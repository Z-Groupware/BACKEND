package com.module06.backend.meeting.application.result;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * MEET-10 조회 결과를 프레젠테이션 계층에 전달하는 애플리케이션 결과 객체다.
 *
 * 회의 원본과 프로젝트 표시 정보를 값으로만 보관해 외부 엔티티 의존을 만들지 않는다.
 */
public record PendingActionMeetingListResult(List<MeetingItem> meetings) {

    /* 결과 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public PendingActionMeetingListResult {
        /* 빈 목록도 허용하되 null은 정상 결과로 숨기지 않고 즉시 실패하게 한다. */
        meetings = List.copyOf(meetings);
    }

    /* 확정 대기 회의 카드 한 건을 구성하는 애플리케이션 결과다. */
    public record MeetingItem(
            Long meetingId,
            String title,
            MeetingStatus status,
            LocalDateTime startAt,
            long pendingActionCount,
            Project project
    ) {
    }

    /* 확정 대기 회의 카드에 표시할 프로젝트 식별자와 태그 정보다. */
    public record Project(Long projectId, String tag, String name) {
    }
}
