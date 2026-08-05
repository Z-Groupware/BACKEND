package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.domain.model.Handover;

import java.time.LocalDateTime;

/*
    #2 오너(팀장급) 일괄 이관+최종승인.
    팀장급 오프보딩처럼 위에 리더가 없어 오너가 직접 처리하는 인수인계에서,
    후임 1명을 선택하면 재배정 필요한 모든 항목을 그 후임에게 일괄 이관하고
    중간 완료(REASSIGNED) → 최종 승인(FINALIZED)까지 한 번에 수행한다.
    내부적으로 기존 reassign/complete/finalize 흐름을 그대로 재사용해 부수효과(액션 재배정·
    멤버 상태 전이·인사이트 스냅샷)를 일관되게 유지한다.
*/
public interface HandoverToSuccessorUseCase {

    Handover handoverToSuccessor(Long handoverId, Long successorId, Long ownerId, String ownerName,
                                 LocalDateTime at);
}
