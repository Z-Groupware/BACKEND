package com.module06.backend.action.application.port;

import java.util.List;
import java.util.Optional;

import com.module06.backend.action.domain.model.ActionType;

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
    // 2026-08-12, 모성진(D) 요청으로 findActionTeamReference가 대체 — 회의 팀과 액션 팀이
    // 일치하는지까지 검증하려면 boolean만으로는 팀을 알 방법이 없었다. D 쪽 호출부가
    // findActionTeamReference로 옮겨가면 이 메서드는 삭제한다(호출처가 D 한 곳뿐이라
    // isPresent()로 그대로 대체 가능 — 지금은 D 마이그레이션 전이라 남겨둔다).
    boolean existsAction(Long companyId, Long actionId);

    // 2026-08-12, 모성진(D) 요청 — 회의–액션 팀 일치 검증. teamId(회의 팀과 일치하는지)·
    // actionType(상위 팀 액션(TEAM)이 맞는지 — PERSONAL은 teamId가 항상 null이라 이 구분이
    // 없으면 "다른 팀 액션"이라는 틀린 에러가 나간다) 둘 다 필요해서 boolean으로는 안 된다.
    Optional<ActionTeamReference> findActionTeamReference(Long companyId, Long actionId);

    // teamId는 PERSONAL이면 항상 null이다(ActionTypeShapePolicy) — actionType으로 구분해야
    // 하는 이유가 이거다.
    record ActionTeamReference(Long teamId, ActionType actionType) {
    }

    // 마이페이지 확정 대기 목록용 배치 조회 — 아직 분배되지 않은 액션이 남은 회의만 반환.
    // companyId 또는 sourceMeetingIds가 null이거나 비면 조회 없이 List.of().
    //
    // 크기 제한 없음 — 호출자가 몇 건을 보내든 그대로 받는다. 배치 청킹은 이 구현체(내부적으로
    // 200건씩) 관심사이지 계약이 아니다 — 호출자가 크기를 맞춰 보낼 필요가 없다(2026-08-08,
    // 모성진 확인 후 정리 — 이전 "sourceMeetingIds 최대 200건" 서술은 착오였다).
    //
    // 반환값은 undispatchedCount >= 1인 회의만 담는다 — groupingBy(counting())로 만들어서
    // 매칭되는 행이 하나도 없는 meetingId는 애초에 키로 생기지 않는다. count=0인 항목은 절대
    // 안 나오므로 호출자가 방어적으로 걸러낼 필요 없다.
    List<MeetingUndispatchedActions> findMeetingsWithUndispatchedActions(
            Long companyId, List<Long> sourceMeetingIds);

    // undispatchedCount는 항상 1 이상이다(위 계약 참고).
    record MeetingUndispatchedActions(Long sourceMeetingId, long undispatchedCount) {
    }

    // 2026-08-10, 모성진(D) 요청 — 회의 목록 화면 카드의 "N분 · 액션 N건" 표시용. 위와 달리
    // 분배(dispatched_at)·검토(review_status) 조건 없이 그 회의에서 나온 액션 전체 개수다.
    // companyId 또는 sourceMeetingIds가 null이거나 비면 조회 없이 List.of().
    //
    // 크기 제한 없음 — findMeetingsWithUndispatchedActions와 같은 이유로 청킹은 구현체
    // 내부 관심사다.
    //
    // actionCount >= 1인 회의만 담는다(groupingBy(counting())라 0건 회의는 키 자체가 안
    // 생긴다) — 화면에 "액션 0건"을 찍으려면 호출자가 없는 회의를 0으로 채워야 한다.
    List<MeetingActionCount> countActionsByMeetings(Long companyId, List<Long> sourceMeetingIds);

    // actionCount는 항상 1 이상이다(위 계약 참고).
    record MeetingActionCount(Long sourceMeetingId, long actionCount) {
    }
}
