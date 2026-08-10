package com.module06.backend.meeting.application.port.out;

import java.time.LocalDate;
import java.util.List;

/*
 * D 회의 도메인이 C 액션 도메인에 회의별 현재 액션 요약을 요청하는 아웃바운드 Port다.
 *
 * C의 Action 엔티티와 enum을 D로 전파하지 않고 카드 표시에 필요한 값만 문자열 계약으로 받는다.
 */
public interface MeetingActionSummaryQueryPort {

    /* 회사 범위의 회의에서 출처 회의 카드에 공개할 현재 액션 목록을 조회한다. */
    List<ActionSummary> findActionSummaries(Long companyId, Long meetingId);

    /* 액션 카드 표시에 필요한 식별자·상태·기한·담당 표시 정보를 담는 D 소유 읽기 모델이다. */
    record ActionSummary(
            Long actionId,
            String actionType,
            String title,
            String status,
            LocalDate dueDate,
            String assigneeName,
            String teamName
    ) {
    }
}
