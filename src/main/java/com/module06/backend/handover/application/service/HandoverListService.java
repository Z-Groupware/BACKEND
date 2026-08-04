package com.module06.backend.handover.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverItem;
import com.module06.backend.handover.domain.repository.HandoverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class HandoverListService implements GetHandoverListUseCase {

    private final HandoverRepository handoverRepository;

    public HandoverListService(HandoverRepository handoverRepository) {
        this.handoverRepository = handoverRepository;
    }

    @Override
    public List<HandoverSummary> list(HandoverListQuery query) {
        boolean byWriter = query != null && query.writerMemberId() != null;
        boolean byTeam = query != null && query.teamId() != null;
        if (!byWriter && !byTeam) {
            throw new BusinessException(HandoverErrorCode.HO_LIST_SCOPE_REQUIRED);
        }
        if (byWriter && byTeam) {
            throw new BusinessException(HandoverErrorCode.HO_LIST_SCOPE_AMBIGUOUS);
        }

        List<Handover> handovers = byWriter
                ? handoverRepository.findByWriterMemberId(query.writerMemberId())
                : handoverRepository.findByTeamId(query.teamId());

        return handovers.stream()
                .filter(handover -> query.status() == null || handover.getStatus() == query.status())
                .map(this::toSummary)
                .toList();
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
                reassignedCount
        );
    }
}
