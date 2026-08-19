package com.module06.backend.handover.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase;
import com.module06.backend.handover.application.usecase.GetHandoverUseCase;
import com.module06.backend.handover.application.usecase.GetPendingAttributionListUseCase;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverItem;
import com.module06.backend.handover.domain.repository.HandoverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class HandoverListService implements GetHandoverListUseCase, GetHandoverUseCase,
        GetPendingAttributionListUseCase {

    private final HandoverRepository handoverRepository;
    private final OrgQueryPort orgQueryPort;

    public HandoverListService(HandoverRepository handoverRepository, OrgQueryPort orgQueryPort) {
        this.handoverRepository = handoverRepository;
        this.orgQueryPort = orgQueryPort;
    }

    @Override
    public Handover get(Long handoverId) {
        return handoverRepository.findById(handoverId)
                .orElseThrow(() -> new BusinessException(HandoverErrorCode.HO_NOT_FOUND));
    }

    @Override
    public List<HandoverSummary> list(HandoverListQuery query) {
        if (query == null || query.scope() == null) {
            throw new BusinessException(HandoverErrorCode.HO_LIST_SCOPE_REQUIRED);
        }

        List<Handover> handovers = switch (query.scope()) {
            case SELF -> handoverRepository.findByWriterMemberId(requireId(query.memberId()));
            case TEAM -> handoverRepository.findByTeamId(requireId(query.teamId()));
            // 오너·어드민 회사 전체(A안): handover에 company_id가 없어, 조직(B) 포트로 회사 멤버 id를
            // 받아 그들이 작성한 인수인계를 조회한다. 멤버가 없으면 IN () 쿼리를 피해 즉시 빈 목록.
            case COMPANY -> {
                List<Long> memberIds = orgQueryPort.findMemberIdsByCompany(requireId(query.companyId()));
                yield memberIds.isEmpty() ? List.of() : handoverRepository.findByWriterMemberIdIn(memberIds);
            }
        };

        return handovers.stream()
                .filter(handover -> query.status() == null || handover.getStatus() == query.status())
                .map(this::toSummary)
                .toList();
    }

    @Override
    public List<HandoverSummary> listPendingAttribution(Long companyId) {
        if (companyId == null) {
            throw new BusinessException(HandoverErrorCode.HO_COMPANY_CONTEXT_REQUIRED);
        }
        List<Long> memberIds = orgQueryPort.findMemberIdsByCompany(companyId);
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return handoverRepository.findByWriterMemberIdIn(memberIds).stream()
                .filter(Handover::isPendingAttribution)
                .map(this::toSummary)
                .toList();
    }

    private Long requireId(Long id) {
        if (id == null) {
            throw new BusinessException(HandoverErrorCode.HO_LIST_SCOPE_REQUIRED);
        }
        return id;
    }

    private HandoverSummary toSummary(Handover handover) {
        List<HandoverItem> items = handover.getItems();
        int reassignRequiredCount = (int) items.stream().filter(HandoverItem::isReassignRequired).count();
        int reassignedCount = (int) items.stream()
                .filter(HandoverItem::isReassignRequired)
                .filter(HandoverItem::isReassigned)
                .count();
        return new HandoverSummary(
                handover.getId(),
                handover.getWriterMemberId(),
                handover.getWriterNameSnap(),
                handover.getWriterPositionSnap(),
                handover.getTeamId(),
                handover.getHandoverType(),
                handover.getStatus(),
                handover.getLeaveStartAt(),
                handover.getLeaveEndAt(),
                handover.getLastWorkingDay(),
                items.size(),
                reassignRequiredCount,
                reassignedCount,
                handover.getFinalizedAt(),
                handover.getFinalApproverNameSnap()
        );
    }
}
