package com.module06.backend.action.infrastructure.adapter;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.action.infrastructure.persistence.ActionJpaEntity;
import com.module06.backend.action.infrastructure.persistence.SpringDataActionRepository;
import com.module06.backend.capture.application.port.out.ActionDispatchPort;

/* comment.
    action(C)이 구현하는 검토(A) 아웃바운드 포트. A가 정의한 ActionDispatchPort
    (capture.application.port.out)를 실제로 배선한다 — ReviewActionCreateAdapter ·
    ReviewActionDeleteAdapter와 같은 방향이다(2026-08-07, RVW-05 착수).

    액션을 새로 만들지 않는다. 분석 직후에 이미 만들어져 있고(08/05 확정), 여기서 하는 일은
    "나갔다"는 표시를 찍는 것뿐이다 — 그래서 실패하거나 두 번 불려도 중복 액션이 생기지 않는다.

    회사 스코프를 다시 본다. A의 조회가 이미 걸러 오지만 이 포트는 공개된 경계이고, 한 곳이
    빠지면 그 경로만 조용히 뚫린다(#100). 다른 회사 액션은 던지지 않고 조용히 제외한다 —
    분배 확정은 묶음 처리라 하나 때문에 회의 전체가 실패하면 사람이 할 수 있는 일이 없다.

    도메인 모델(Action)이 아니라 엔티티를 직접 다루는 이유는 ActionJpaEntity.dispatchedAt
    주석에 적었다 — 다른 필드와 얽힌 전이 규칙이 없는 기록용 시각이고, Action의 생성자를
    넓히면 그 도메인을 쓰는 모든 경로가 함께 흔들린다.

    연결된 클래스
    - ActionDispatchPort         : 구현하는 계약 (capture.application.port.out)
    - ActionJpaEntity            : markDispatched로 시각을 찍는 엔티티
    - SpringDataActionRepository : 조회·저장 위임 대상
*/
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionDispatchAdapter implements ActionDispatchPort {

    private final SpringDataActionRepository springDataActionRepository;

    @Override
    @Transactional
    public int markDispatched(long companyId, List<Long> actionIds, LocalDateTime dispatchedAt) {
        if (actionIds.isEmpty()) {
            return 0;
        }

        List<ActionJpaEntity> actions = springDataActionRepository.findAllById(actionIds);

        int marked = 0;
        for (ActionJpaEntity action : actions) {
            if (!Long.valueOf(companyId).equals(action.getCompanyId())) {
                // 다른 회사 액션이다. 여기까지 올 수 없는 값이지만 왔다면 조용히 뺀다.
                log.warn("회사가 다른 액션이 분배 대상에 섞였다 — actionId={}", action.getId());
                continue;
            }
            if (action.markDispatched(dispatchedAt)) {
                marked++;
            }
        }

        springDataActionRepository.saveAll(actions);
        return marked;
    }
}
