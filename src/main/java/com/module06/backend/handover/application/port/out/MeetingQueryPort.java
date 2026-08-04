package com.module06.backend.handover.application.port.out;

import java.time.LocalDate;
import java.util.List;

/**
 * 인계 패키지 조회에 필요한 회의 이력을 외부(회의 도메인)에서 가져오는 아웃바운드 포트.
 * 구현체는 회의 모듈이 담당하며, 미구현 시 컨텍스트는 정상 부팅한다(Optional 주입).
 */
public interface MeetingQueryPort {

    MeetingHistory findMeeting(Long meetingId);

    record MeetingHistory(
            Long meetingId,
            LocalDate date,
            List<String> attendees,
            String decisionSummary,
            String actionItemsSummary
    ) {
    }
}
