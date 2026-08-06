package com.module06.backend.action.domain.repository;

import java.util.List;
import java.util.Optional;

import com.module06.backend.action.domain.model.Action;

/* comment.
    액션 저장소 계약. domain 계층이 선언하고 infrastructure가 구현한다(의존성 역전).
    JPA·쿼리 같은 기술 세부사항은 이 인터페이스에 드러나지 않는다.
    인수인계용 조회(개인 담당·미완료 필터·퇴사자 참여 팀 액션)도 이 계약으로만 노출한다.

    연결된 클래스
    - Action                    : 다루는 도메인 모델
    - ActionPersistenceAdapter  : 구현체 (infrastructure.persistence)
    - SpringDataActionRepository: 어댑터가 위임하는 Spring Data 인터페이스
    - application.service.*     : 이 계약을 주입받는 유스케이스 구현체들
*/
public interface ActionRepository {

    Optional<Action> findById(Long actionId);

    Action save(Action action);

    List<Action> findPersonalByAssignee(Long memberId, boolean excludeDone);

    List<Action> findAllPersonalByAssignee(Long memberId);

    List<Action> findParentTeamActionsByAssignee(Long memberId);
}
