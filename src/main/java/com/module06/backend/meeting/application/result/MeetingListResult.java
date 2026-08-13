package com.module06.backend.meeting.application.result;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * MEET-02 조회 결과를 프레젠테이션 계층에 전달하는 애플리케이션 결과 객체다.
 *
 * 회의 영속성 엔티티와 다른 도메인의 모델을 노출하지 않고 화면에 필요한 값만 보관한다.
 */
public record MeetingListResult(
        List<MeetingItem> meetings,
        Page page
) {

    /* 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public MeetingListResult {
        /* 빈 목록은 정상 결과로 허용하되 null 목록은 계약 위반으로 즉시 실패하게 한다. */
        meetings = List.copyOf(meetings);
    }

    /* 회의 목록 한 행에 필요한 메타와 중첩 표시 정보를 담는다. */
    public record MeetingItem(
            Long meetingId,
            String title,
            MeetingStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int attendeeCount,
            long actionCount,
            boolean isHost,
            boolean entryAvailable,
            int durationMinutes,
            List<Attendee> attendees,
            MeetingRoom meetingRoom,
            Project project
    ) {

        /* 아바타 표시용 참석자 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
        public MeetingItem {
            attendees = List.copyOf(attendees);
        }
    }

    /* 회의실 도메인에서 받은 식별자와 표시 이름이다. */
    public record MeetingRoom(Long meetingRoomId, String name) {
    }

    /* 프로젝트 도메인에서 받은 식별자와 태그·표시 이름이다. */
    public record Project(Long projectId, String tag, String name) {
    }

    /* 카드 아바타 표시용 참석자 식별자와 이름이다. 직급 등 전체 명단은 상세(MEET-04)에서 조회한다. */
    public record Attendee(Long memberId, String name) {
    }

    /* 현재 페이지와 전체 결과 규모를 나타내는 페이지 메타데이터다. */
    public record Page(int page, int size, long totalElements, int totalPages) {
    }
}
