package com.module06.backend.handover.infrastructure.adapter;

import com.module06.backend.handover.application.port.out.OrgQueryPort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrgQueryPortPendingAdapter implements OrgQueryPort {

    @Override
    public Long findTeamLeaderId(Long teamId) {
        throw pending("OrgQueryPort#findTeamLeaderId");
    }

    @Override
    public MemberSnapshot findMember(Long memberId) {
        throw pending("OrgQueryPort#findMember");
    }

    @Override
    public List<ReassignCandidate> findReassignCandidates(Long teamId, Long excludeMemberId) {
        throw pending("OrgQueryPort#findReassignCandidates");
    }

    private UnsupportedOperationException pending(String method) {
        return new UnsupportedOperationException(
                method + " pending implementation; waiting for B(org/auth) domain wiring; this is not a silent fallback."
        );
    }
}
