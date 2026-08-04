package com.module06.backend.handover.infrastructure.adapter;

import com.module06.backend.handover.application.port.out.ActionReassignPort;
import com.module06.backend.handover.domain.model.HandoverType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ActionReassignPortPendingAdapter implements ActionReassignPort {

    @Override
    public List<HandoverableAction> findHandoverableActions(Long memberId, HandoverType type) {
        throw pending("ActionReassignPort#findHandoverableActions");
    }

    @Override
    public void reassign(Long actionId, Long fromMemberId, Long toMemberId) {
        throw pending("ActionReassignPort#reassign");
    }

    private UnsupportedOperationException pending(String method) {
        return new UnsupportedOperationException(
                method + " pending implementation; waiting for C(action) domain wiring; this is not a silent fallback."
        );
    }
}
