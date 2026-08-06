package com.module06.backend.capture.application.port.out;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 검토가 끝난 액션을 보드로 내보내는 아웃바운드 포트다(RVW-05). A 가 선언하고 C(액션)가 배선한다 —
 * {@link ReviewActionCreatePort} · {@link ReviewActionDeletePort} 와 같은 방향이다.
 *
 * <h2>넘기는 형태는 "표시"다</h2>
 * 액션 자체는 분석 직후에 이미 만들어져 있다(08/05 확정 · ActionDistributionPort). 여기서 하는
 * 일은 그 액션에 **나갔다는 표시**를 찍는 것이고, 보드는 그 표시를 보고 카드를 띄운다.
 * 새로 만들지 않으므로 실패해도 중복 액션이 생기지 않는다 — 다시 부르면 된다.
 */
public interface ActionDispatchPort {

    /*
     * 액션들을 분배 확정으로 표시한다.
     *
     * @param dispatchedAt 확정 시각. **호출자가 정한다** — 한 번의 확정으로 나간 액션은 모두
     *                     같은 시각을 가져야 "이 회의를 언제 내보냈나"가 하나로 읽힌다.
     *                     어댑터가 각자 now() 를 찍으면 같은 확정 안에서 시각이 갈린다.
     * @return 실제로 표시된 건수. 요청 수와 다르면 그 사이에 액션이 지워졌다는 뜻이다.
     */
    int markDispatched(long companyId, List<Long> actionIds, LocalDateTime dispatchedAt);
}
