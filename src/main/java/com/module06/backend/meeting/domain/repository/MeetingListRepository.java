package com.module06.backend.meeting.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.meeting.domain.model.MeetingListScope;
import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * MEET-02의 동적 필터·권한·페이징 조회를 수행하는 목록 전용 도메인 저장소 계약이다.
 *
 * Spring Data의 Page나 Specification을 도메인 경계에 노출하지 않고 조회 조건과 결과를
 * 프레임워크 독립적인 값으로 표현한다.
 */
public interface MeetingListRepository {

    /* 회사와 열람 범위 및 선택 필터를 적용한 회의 페이지를 조회한다. */
    MeetingPage findMeetings(MeetingListCriteria criteria);

    /* 저장소가 적용할 테넌트·권한·필터·페이지 조건이다. */
    record MeetingListCriteria(
            Long companyId,
            Long requesterMemberId,
            boolean companyWideRead,
            Long projectId,
            Long meetingRoomId,
            LocalDateTime fromInclusive,
            LocalDateTime toInclusive,
            MeetingStatus status,
            MeetingListScope scope,
            int page,
            int size
    ) {
    }

    /* 조회된 회의 한 건과 배치 조회된 참석자 식별자를 담는 읽기 모델이다. */
    record MeetingListSnapshot(
            Long meetingId,
            Long projectId,
            Long teamId,
            Long meetingRoomId,
            String title,
            MeetingStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Long hostMemberId,
            List<Long> attendeeMemberIds
    ) {

        /* 참석자 식별자 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
        public MeetingListSnapshot {
            attendeeMemberIds = List.copyOf(attendeeMemberIds);
        }
    }

    /* 현재 페이지의 회의와 전체 결과 규모를 함께 반환하는 저장소 결과다. */
    record MeetingPage(
            List<MeetingListSnapshot> meetings,
            long totalElements,
            int totalPages
    ) {

        /* 영속성 결과 목록을 외부에서 변경하지 못하도록 불변 복사한다. */
        public MeetingPage {
            /* 빈 페이지는 허용하되 null 목록은 저장소 계약 위반으로 처리한다. */
            meetings = List.copyOf(meetings);
        }
    }
}
