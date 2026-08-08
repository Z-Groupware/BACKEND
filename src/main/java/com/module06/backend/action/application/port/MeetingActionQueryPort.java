package com.module06.backend.action.application.port;

import java.util.List;

/* comment.
    action(C, 김민섭)이 선언하고, meeting(D, 모성진) 도메인이 호출하는 인바운드 포트.
    D는 action 엔티티·Repository를 직접 참조하지 않고 이 계약으로만 액션 상태를 묻는다
    (0절 절대규칙 1항 — 도메인 간 엔티티 직접 참조 금지, ProjectQueryPort와 동일 패턴).

    이름에 'Pending'을 쓰지 않는다 — D쪽 PendingActionQueryAdapter의 'pending'(연동 대기 스텁)과
    뜻이 정반대라 같은 PR 안에서 충돌한다. 판정 기준이 dispatched_at 이므로 undispatched로 부른다.

    연결된 클래스
    - ActionPersistenceAdapter : 이 계약의 구현체
    - meeting.application.port.out.ActionQueryPort : D쪽 대응 계약
*/
public interface MeetingActionQueryPort {

    // MEET-01 회의 예약 시 relatedActionId 검증용.
    boolean existsAction(Long companyId, Long actionId);

    // 마이페이지 확정 대기 목록용 배치 조회 — 아직 분배되지 않은 액션이 남은 회의만 반환.
    // companyId 또는 sourceMeetingIds가 null이거나 비면 조회 없이 List.of().
    // sourceMeetingIds 최대 200건(D의 MEETING_ID_BATCH_SIZE와 동일). 초과분은 C가 분할 조회한다.
    List<MeetingUndispatchedActions> findMeetingsWithUndispatchedActions(
            Long companyId, List<Long> sourceMeetingIds);

    record MeetingUndispatchedActions(Long sourceMeetingId, long undispatchedCount) {
    }
}
