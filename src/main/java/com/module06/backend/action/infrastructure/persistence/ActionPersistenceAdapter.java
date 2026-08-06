package com.module06.backend.action.infrastructure.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.domain.repository.ActionRepository;

import lombok.RequiredArgsConstructor;

/* comment.
    domain의 ActionRepository 계약을 JPA로 구현하는 어댑터(의존성 역전의 실행 지점).
    책임 두 가지 — Spring Data 호출 위임, 그리고 ActionJpaEntity ↔ Action 변환.
    JPA 예외를 그대로 위로 흘리지 않고 도메인이 이해하는 형태로 바꿔서 돌려준다.

    연결된 클래스
    - ActionRepository           : 구현하는 도메인 계약
    - SpringDataActionRepository : 실제 쿼리 위임 대상
    - ActionJpaEntity            : 변환 대상 엔티티
    - Action                     : 변환 결과 도메인 모델
*/
@Component
@RequiredArgsConstructor
public class ActionPersistenceAdapter implements ActionRepository {

    private final SpringDataActionRepository springDataActionRepository;

    @Override
    public Optional<Action> findById(Long actionId) {
        return springDataActionRepository.findById(actionId)
                .map(ActionJpaEntity::toDomain);
    }

    @Override
    public Action save(Action action) {
        return springDataActionRepository.save(ActionJpaEntity.from(action))
                .toDomain();
    }

    @Override
    public List<Action> findPersonalByAssignee(Long memberId, boolean excludeDone) {
        if (excludeDone) {
            return springDataActionRepository
                    .findAllByAssigneeMemberIdAndActionTypeAndStatusNotOrderByDueDateAscIdAsc(
                            memberId,
                            ActionType.PERSONAL,
                            ActionStatus.DONE
                    )
                    .stream()
                    .map(ActionJpaEntity::toDomain)
                    .toList();
        }
        return findAllPersonalByAssignee(memberId);
    }

    @Override
    public List<Action> findAllPersonalByAssignee(Long memberId) {
        return springDataActionRepository
                .findAllByAssigneeMemberIdAndActionTypeOrderByDueDateAscIdAsc(memberId, ActionType.PERSONAL)
                .stream()
                .map(ActionJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Action> findParentTeamActionsByAssignee(Long memberId) {
        // QUERY_002(신규 쿼리 애노테이션 금지) 준수: self-join JPQL 대신 파생 쿼리 2단으로 부모 TEAM 액션을 뽑는다.
        // 1) 퇴사자 개인 액션(전 status) 조회 → 2) parent_action_id 중복 제거 → 3) TEAM 부모 조회.
        List<Long> parentIds = springDataActionRepository
                .findAllByAssigneeMemberIdAndActionTypeOrderByDueDateAscIdAsc(memberId, ActionType.PERSONAL)
                .stream()
                .map(ActionJpaEntity::getParentActionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (parentIds.isEmpty()) {
            return List.of();
        }
        return springDataActionRepository
                .findAllByIdInAndActionTypeOrderByIdAsc(parentIds, ActionType.TEAM)
                .stream()
                .map(ActionJpaEntity::toDomain)
                .toList();
    }
}
