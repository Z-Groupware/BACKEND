package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.application.command.ReassignItemCommand;
import com.module06.backend.handover.application.command.ReassignItemsCommand;
import com.module06.backend.handover.domain.model.Handover;

public interface ReassignHandoverItemUseCase {

    Handover reassignItem(ReassignItemCommand command);

    // 일괄 재분배 — 여러 액션을 한 트랜잭션에서 담당자 지정(휴직 자가 재할당 등).
    Handover reassignItems(ReassignItemsCommand command);
}
