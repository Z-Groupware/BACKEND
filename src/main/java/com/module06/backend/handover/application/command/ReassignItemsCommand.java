package com.module06.backend.handover.application.command;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일괄 재분배 — 휴직 신청(팀장 자가 재할당)처럼 여러 액션을 한 번에 담당자 지정할 때 쓴다.
 * 건별 {@link ReassignItemCommand}와 달리 한 트랜잭션에서 전부 적용된다 — 하나라도 실패하면
 * (없는 액션·잘못된 상태) 전체가 롤백되어 부분 재분배로 어긋난 채 남지 않는다.
 */
public record ReassignItemsCommand(
        Long handoverId,
        List<Assignment> assignments,
        LocalDateTime reassignedAt
) {

    public record Assignment(Long actionId, Long toMemberId) {
    }
}
