package com.module06.backend.handover.infrastructure.adapter;

import com.module06.backend.handover.application.port.out.MemberStatusPort;
import org.springframework.stereotype.Component;

@Component
public class MemberStatusPortPendingAdapter implements MemberStatusPort {

    @Override
    public void toWaiting(Long memberId) {
        throw pending("MemberStatusPort#toWaiting");
    }

    @Override
    public void toVacation(Long memberId) {
        throw pending("MemberStatusPort#toVacation");
    }

    @Override
    public void offboard(Long memberId) {
        throw pending("MemberStatusPort#offboard");
    }

    @Override
    public void restoreActive(Long memberId) {
        throw pending("MemberStatusPort#restoreActive");
    }

    private UnsupportedOperationException pending(String method) {
        return new UnsupportedOperationException(
                method + " pending implementation; waiting for B(member status) domain wiring; this is not a silent fallback."
        );
    }
}
