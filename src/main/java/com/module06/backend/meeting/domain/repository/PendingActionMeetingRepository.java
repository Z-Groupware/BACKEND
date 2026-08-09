package com.module06.backend.meeting.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

/*
 * MEET-10 확정 대기 회의 목록의 후보 회의를 조회하는 도메인 저장소 계약이다.
 *
 * 분배 대기 여부는 액션 도메인이 판정하므로 이 계약은 host·회사·종료 상태까지만 좁힌
 * 후보를 반환한다. 액션 조건을 여기에 섞으면 D가 action 테이블을 알게 된다.
 */
public interface PendingActionMeetingRepository {

    /* 요청 회사에서 로그인 사용자가 host인 종료된 회의를 최근 시작 순으로 조회한다. */
    List<PendingActionMeetingCandidate> findHostedDoneMeetings(Long companyId, Long hostMemberId);

    /* MEET-10 응답 조립에 필요한 회의 메타 읽기 모델이다. */
    record PendingActionMeetingCandidate(
            Long meetingId,
            Long projectId,
            String title,
            LocalDateTime startAt
    ) {
    }
}
