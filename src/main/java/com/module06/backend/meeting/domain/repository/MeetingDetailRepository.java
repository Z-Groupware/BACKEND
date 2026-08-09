package com.module06.backend.meeting.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * MEET-04 회의 상세 조회에 필요한 회의 원본 데이터를 제공하는 저장소 계약이다.
 *
 * 목록·인수인계용 최소 조회 모델과 상세 화면의 필드를 분리해 기존 조회 계약이
 * 상세 화면 변경에 따라 불필요하게 확장되지 않도록 한다.
 */
public interface MeetingDetailRepository {

    /* 회사 범위에서 회의 한 건과 전체 참석자 식별자를 상세 조회한다. */
    Optional<MeetingDetailSnapshot> findMeetingDetail(Long companyId, Long meetingId);

    /* 회의 상세 응답과 열람 권한 판정에 필요한 D 도메인 원본 값이다. */
    record MeetingDetailSnapshot(
            Long meetingId,
            Long companyId,
            Long projectId,
            Long teamId,
            Long meetingRoomId,
            Long hostMemberId,
            String title,
            MeetingStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            boolean recordingConsent,
            LocalDateTime createdAt,
            List<Long> attendeeMemberIds
    ) {

        /* 조회 이후 참석자 명단이 외부에서 변경되지 않도록 불변 목록으로 보관한다. */
        public MeetingDetailSnapshot {
            /* 영속성 어댑터가 제공한 참석자 식별자를 생성 시점에 방어적으로 복사한다. */
            attendeeMemberIds = List.copyOf(attendeeMemberIds);
        }
    }
}
