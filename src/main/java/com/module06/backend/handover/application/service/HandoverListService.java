package com.module06.backend.handover.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverItem;
import com.module06.backend.handover.domain.repository.HandoverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class HandoverListService implements GetHandoverListUseCase {

    private final HandoverRepository handoverRepository;
    private final OrgQueryPort orgQueryPort;

    public HandoverListService(HandoverRepository handoverRepository, OrgQueryPort orgQueryPort) {
        this.handoverRepository = handoverRepository;
        this.orgQueryPort = orgQueryPort;
    }

    @Override
    public List<HandoverSummary> list(HandoverListQuery query) {
        if (query == null || query.companyId() == null) {
            // companyId 는 토큰에서만 온다(컨트롤러가 채운다). 없으면 회사 경계를 세울 수 없어 조기 실패.
            throw new BusinessException(HandoverErrorCode.HO_LIST_SCOPE_REQUIRED);
        }
        boolean byWriter = query.writerMemberId() != null;
        boolean byTeam = query.teamId() != null;
        if (!byWriter && !byTeam) {
            throw new BusinessException(HandoverErrorCode.HO_LIST_SCOPE_REQUIRED);
        }
        if (byWriter && byTeam) {
            throw new BusinessException(HandoverErrorCode.HO_LIST_SCOPE_AMBIGUOUS);
        }

        // 회사 경계: handover 테이블엔 company_id 가 없어(2026-08-06 결정) 매번 회사 구성원을 물어
        // 그 집합으로 거른다. 이 관문이 없으면 로그인한 사원이 남의 회사 teamId·사번을 넣어 읽는다.
        Set<Long> companyMemberIds = Set.copyOf(orgQueryPort.findMemberIdsByCompany(query.companyId()));

        List<Handover> handovers;
        if (byWriter) {
            // 사번 스코프: 남의 회사 사번을 넣는 건 조회가 아니라 월경이라 크게 막는다(회의 도메인과 같은 태도).
            if (!companyMemberIds.contains(query.writerMemberId())) {
                throw new BusinessException(HandoverErrorCode.HO_LIST_SCOPE_FORBIDDEN);
            }
            handovers = handoverRepository.findByWriterMemberId(query.writerMemberId());
        } else {
            // 팀 스코프: team_id 는 작성 시점 스냅샷이라 팀→회사 역참조 포트가 없다. 대신 결과를
            // 회사 구성원이 쓴 것만 남겨 경계를 지킨다 — 남의 회사 teamId 를 넣으면 자연히 빈 목록이 된다.
            handovers = handoverRepository.findByTeamId(query.teamId()).stream()
                    .filter(handover -> companyMemberIds.contains(handover.getWriterMemberId()))
                    .toList();
        }

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
