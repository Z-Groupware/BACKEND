package com.module06.backend.meeting.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

/*
 * MEET-15 요약 중단·실패 목록의 D 소유 후보 회의를 조회하는 저장소 계약이다.
 *
 * 요약 상태는 A가 판정하므로 회사·host·종료 상태와 화면에 필요한 회의 메타까지만 책임진다.
 */
public interface StalledSummaryMeetingRepository {

    /* 요청 회사에서 로그인 사용자가 host인 종료 회의를 최근 시작 순으로 조회한다. */
    List<StalledSummaryMeetingCandidate> findHostedDoneSummaryCandidates(Long companyId, Long hostMemberId);

    /* 필터와 응답 조립에 필요한 D 소유 회의 읽기 모델이다. */
    record StalledSummaryMeetingCandidate(
            Long meetingId,
            Long projectId,
            String title,
            LocalDateTime startAt
    ) {
    }
}
