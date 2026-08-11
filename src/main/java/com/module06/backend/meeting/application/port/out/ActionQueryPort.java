package com.module06.backend.meeting.application.port.out;

import java.util.List;

/*
 * 회의 도메인이 액션 상태를 물을 때 사용하는 아웃바운드 포트다.
 *
 * 실제 구현은 C 액션 도메인이 공개하는 조회 계약과 연결되며, 회의 서비스는
 * action 엔티티나 저장소를 직접 참조하지 않는다.
 */
public interface ActionQueryPort {

    /* MEET-01이 회의에 연결하려는 액션이 같은 회사에 존재하는지 확인한다. */
    boolean existsAction(Long companyId, Long actionId);

    /* MEET-10 후보 회의 중 아직 보드로 분배되지 않은 액션이 남은 회의만 일괄 조회한다. */
    List<UndispatchedActionMeeting> findMeetingsWithUndispatchedActions(
            Long companyId,
            List<Long> meetingIds
    );

    /* MEET-02 현재 페이지 회의별 전체 액션 수를 한 번에 조회한다. */
    List<MeetingActionCount> countActionsByMeetings(
            Long companyId,
            List<Long> meetingIds
    );

    /*
     * MEET-10 목록에 필요한 회의별 분배 대기 액션 집계다.
     *
     * C의 읽기 모델을 그대로 노출하지 않고 D 계약으로 감싸, 액션 도메인이 반환 타입을
     * 바꿔도 회의 서비스와 응답 조립이 함께 깨지지 않게 한다.
     */
    record UndispatchedActionMeeting(Long meetingId, long undispatchedCount) {
    }

    /* MEET-02 목록 카드에 표시할 회의 식별자와 전체 액션 수다. */
    record MeetingActionCount(Long meetingId, long actionCount) {
    }
}
