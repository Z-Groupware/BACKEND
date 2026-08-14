package com.module06.backend.handover.application.service;

import com.module06.backend.handover.application.command.AttributeHandoverToLeaderCommand;
import com.module06.backend.handover.application.command.CreateHandoverCommand;
import com.module06.backend.handover.application.command.FinalizeHandoverInsightsCommand;
import com.module06.backend.handover.application.command.ReassignItemCommand;
import com.module06.backend.handover.application.command.RejectHandoverCommand;
import com.module06.backend.handover.application.port.out.ActionReassignPort;
import com.module06.backend.handover.application.port.out.MemberStatusPort;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.handover.application.usecase.AttributeHandoverToLeaderUseCase;
import com.module06.backend.handover.application.usecase.CompleteHandoverUseCase;
import com.module06.backend.handover.application.usecase.CreateHandoverUseCase;
import com.module06.backend.handover.application.usecase.FinalizeHandoverInsightsUseCase;
import com.module06.backend.handover.application.usecase.FinalizeHandoverUseCase;
import com.module06.backend.handover.application.usecase.ReassignHandoverItemUseCase;
import com.module06.backend.handover.application.usecase.RejectHandoverUseCase;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverActionStatus;
import com.module06.backend.handover.domain.model.HandoverItem;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;
import com.module06.backend.handover.domain.repository.HandoverRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class HandoverService implements CreateHandoverUseCase, ReassignHandoverItemUseCase,
        CompleteHandoverUseCase, FinalizeHandoverUseCase, RejectHandoverUseCase,
        AttributeHandoverToLeaderUseCase {

    private final HandoverRepository handoverRepository;
    private final ActionReassignPort actionReassignPort;
    private final OrgQueryPort orgQueryPort;
    private final MemberStatusPort memberStatusPort;
    private final FinalizeHandoverInsightsUseCase finalizeHandoverInsightsUseCase;

    public HandoverService(HandoverRepository handoverRepository, ActionReassignPort actionReassignPort,
                           OrgQueryPort orgQueryPort, MemberStatusPort memberStatusPort,
                           FinalizeHandoverInsightsUseCase finalizeHandoverInsightsUseCase) {
        this.handoverRepository = handoverRepository;
        this.actionReassignPort = actionReassignPort;
        this.orgQueryPort = orgQueryPort;
        this.memberStatusPort = memberStatusPort;
        this.finalizeHandoverInsightsUseCase = finalizeHandoverInsightsUseCase;
    }

    @Autowired
    public HandoverService(HandoverRepository handoverRepository,
                           Optional<ActionReassignPort> actionReassignPort,
                           Optional<OrgQueryPort> orgQueryPort,
                           Optional<MemberStatusPort> memberStatusPort,
                           FinalizeHandoverInsightsUseCase finalizeHandoverInsightsUseCase) {
        this(handoverRepository, actionReassignPort.orElse(null), orgQueryPort.orElse(null),
                memberStatusPort.orElse(null), finalizeHandoverInsightsUseCase);
    }

    @Override
    public Handover create(CreateHandoverCommand command) {
        if (command == null || command.handoverType() == null) {
            throw new BusinessException(HandoverErrorCode.HO_CREATE_COMMAND_INVALID);
        }
        if (handoverRepository.existsActiveByWriter(command.writerMemberId())) {
            // 갭2: 작성자별 활성 handover(SUBMITTED/REASSIGNED)는 1개만 허용.
            throw new BusinessException(HandoverErrorCode.HO_ACTIVE_ALREADY_EXISTS);
        }
        OrgQueryPort orgQueryPort = orgQueryPort();
        OrgQueryPort.MemberSnapshot writer = orgQueryPort.findMember(command.writerMemberId());
        String teamName = orgQueryPort.findTeamName(command.teamId());
        List<HandoverItem> items = snapshotItems(command);
        Handover handover = command.handoverType() == HandoverType.VACATION
                ? Handover.createVacation(command.writerMemberId(), command.teamId(), teamName, writer.name(),
                        writer.position(), command.note(), command.leaveStartAt(), command.leaveEndAt(), items)
                : Handover.createOffboarding(command.writerMemberId(), command.teamId(), teamName, writer.name(),
                        writer.position(), command.note(), command.lastWorkingDay(), items);
        Handover saved = handoverRepository.save(handover);
        memberStatusPort().toWaiting(command.writerMemberId());
        return saved;
    }

    @Override
    public Handover reassignItem(ReassignItemCommand command) {
        if (command == null) {
            throw new BusinessException(HandoverErrorCode.HO_REASSIGN_COMMAND_INVALID);
        }
        Handover handover = findHandover(command.handoverId());
        assertCanApprove(handover, command.requester());
        OrgQueryPort.MemberSnapshot reassignee = orgQueryPort().findMember(command.toMemberId());
        handover.reassignItem(command.actionId(), command.toMemberId(), reassignee.name(), reassignee.position(),
                command.reassignedAt());
        return handoverRepository.save(handover);
    }

    @Override
    public Handover complete(Long handoverId, AuthPrincipal approverPrincipal, LocalDateTime approvedAt) {
        Handover handover = findHandover(handoverId);
        assertCanApprove(handover, approverPrincipal);
        OrgQueryPort.MemberSnapshot approver = orgQueryPort().findMember(approverPrincipal.memberId());
        handover.complete(approverPrincipal.memberId(), approver.name(), approvedAt);
        handover.getItems().stream()
                .filter(HandoverItem::isReassignRequired)
                .forEach(item -> {
                    actionReassignPort().reassign(
                            item.getActionId(),
                            handover.getWriterMemberId(),
                            item.getReassigneeId()
                    );
                    item.commit(approvedAt);
                });
        return handoverRepository.save(handover);
    }

    @Override
    public Handover finalize(Long handoverId, AuthPrincipal approverPrincipal, LocalDateTime finalizedAt) {
        Handover handover = findHandover(handoverId);
        assertCompanyApprover(handover, approverPrincipal);
        OrgQueryPort.MemberSnapshot approver = orgQueryPort().findMember(approverPrincipal.memberId());
        handover.finalizeApproval(approverPrincipal.memberId(), approver.name(), finalizedAt,
                isLeaderOffboarding(handover));
        if (handover.getHandoverType() == HandoverType.VACATION) {
            memberStatusPort().toVacation(handover.getWriterMemberId());
        } else {
            memberStatusPort().offboard(handover.getWriterMemberId());
            // "레거시 컴파일러" 파생 인텔리전스 스냅샷을 finalize 트랜잭션 내에서 조립·저장(브리프 §4).
            // 퇴사(OFFBOARDING)에만 적용. 크로스모듈 포트(C/D/B) 미구현 시 여기서 throw → 계약 대기 상태.
            finalizeHandoverInsightsUseCase.finalizeInsights(
                    new FinalizeHandoverInsightsCommand(handoverId, handover.getWriterMemberId()));
        }
        return handoverRepository.save(handover);
    }

    @Override
    public Handover attributeToNewLeader(AttributeHandoverToLeaderCommand command) {
        if (command == null || command.newLeaderId() == null) {
            throw new BusinessException(HandoverErrorCode.HO_ATTRIBUTE_COMMAND_INVALID);
        }
        if (command.requesterCompanyId() == null) {
            throw new BusinessException(HandoverErrorCode.HO_COMPANY_CONTEXT_REQUIRED);
        }
        Handover handover = findHandover(command.handoverId());
        // 퇴사자 본인을 신규 팀장으로 지정하면 액션 소유권이 퇴사자에게 남는다(귀속만 풀려 유령 상태) — 거부.
        if (Objects.equals(command.newLeaderId(), handover.getWriterMemberId())) {
            throw new BusinessException(HandoverErrorCode.HO_ATTRIBUTE_TO_WRITER_NOT_ALLOWED);
        }
        // 크로스컴퍼니 차단: 요청자 회사에 속한 작성자·신규 팀장만 이관 대상. read 게이트(assertCanRead)와 같은 근거.
        List<Long> companyMemberIds = orgQueryPort().findMemberIdsByCompany(command.requesterCompanyId());
        if (!companyMemberIds.contains(handover.getWriterMemberId())
                || !companyMemberIds.contains(command.newLeaderId())) {
            throw new BusinessException(HandoverErrorCode.HO_ACCESS_DENIED);
        }
        OrgQueryPort.MemberSnapshot newLeader = orgQueryPort().findMember(command.newLeaderId());
        // 도메인 변경 전에 "미귀속" 액션 id만 수집한다 — 이미 남에게 귀속된 항목의 실제 소유권을 덮어쓰지 않는다
        // (도메인도 미귀속 항목만 스냅샷을 채우므로 스냅샷↔액션 소유권을 일치시킨다).
        List<Long> unassignedActionIds = handover.getItems().stream()
                .filter(HandoverItem::isReassignRequired)
                .filter(item -> !item.isReassigned())
                .map(HandoverItem::getActionId)
                .toList();
        // 상태·불변식 검증을 먼저 통과시킨다 — 귀속 대기가 아니면 여기서 예외로 끝나 액션 커밋 부수효과가 없다.
        handover.attributeToNewLeader(command.newLeaderId(), newLeader.name(), newLeader.position(),
                command.attributedAt());
        // 항목 스냅샷을 채운 뒤, 액션(C) 도메인의 실제 소유권을 퇴사 팀장 → 신규 팀장으로 커밋. complete()와 동일 패턴.
        unassignedActionIds.forEach(actionId -> actionReassignPort().reassign(
                actionId,
                handover.getWriterMemberId(),
                command.newLeaderId()
        ));
        return handoverRepository.save(handover);
    }

    private boolean isLeaderOffboarding(Handover handover) {
        if (handover.getHandoverType() != HandoverType.OFFBOARDING
                || handover.getStatus() != HandoverStatus.SUBMITTED) {
            // REASSIGNED 오프보딩(complete 거친 일반 사원 경로)은 직행과 무관하다.
            // 여기서 org 조회를 타면 기존에 조직 조회 없이 되던 finalize가 OrgQueryPort
            // 부재·조회 실패에 새로 묶인다 — SUBMITTED에서만 팀장 판정한다.
            return false;
        }
        Long teamLeaderId = orgQueryPort().findTeamLeaderId(handover.getTeamId());
        return Objects.equals(teamLeaderId, handover.getWriterMemberId());
    }

    @Override
    public Handover reject(RejectHandoverCommand command) {
        if (command == null) {
            throw new BusinessException(HandoverErrorCode.HO_REJECT_COMMAND_INVALID);
        }
        Handover handover = findHandover(command.handoverId());
        assertCanApprove(handover, command.requester());
        // 상태 전이 검증을 먼저 통과시킨다 — FINALIZED/REJECTED 같은 종단 상태면 여기서 예외로 끝나
        // 액션 롤백 같은 부수효과가 전혀 일어나지 않는다. 유효한 반려 경로에서만 롤백을 실행한다.
        handover.reject(command.reason());
        rollbackCommittedReassignments(handover);
        memberStatusPort().restoreActive(handover.getWriterMemberId());
        return handoverRepository.save(handover);
    }

    private void rollbackCommittedReassignments(Handover handover) {
        for (HandoverItem item : handover.getItems()) {
            if (item.isCommitted() && item.isReassigned()) {
                actionReassignPort().rollbackReassignment(
                        item.getActionId(),
                        item.getReassigneeId(),
                        handover.getWriterMemberId()
                );
                item.markRolledBack();
            }
        }
    }

    private List<HandoverItem> snapshotItems(CreateHandoverCommand command) {
        List<ActionReassignPort.HandoverableAction> actions =
                actionReassignPort().findHandoverableActions(command.writerMemberId(), command.handoverType());
        if (command.handoverType() == HandoverType.VACATION) {
            Set<Long> selected = command.selectedActionIds() == null
                    ? Set.of()
                    : command.selectedActionIds().stream().collect(Collectors.toSet());
            Set<Long> available = actions.stream()
                    .map(ActionReassignPort.HandoverableAction::actionId)
                    .collect(Collectors.toSet());
            List<Long> missing = selected.stream()
                    .filter(id -> !available.contains(id))
                    .toList();
            if (!missing.isEmpty()) {
                // 갭10: 선택 id 중 실제 인계 가능 목록에 없는 것(삭제/타인 액션)이 있으면 조기 실패.
                throw new BusinessException(HandoverErrorCode.HO_SELECTED_ACTION_NOT_HANDOVERABLE);
            }
            actions = actions.stream()
                    .filter(action -> selected.contains(action.actionId()))
                    .toList();
        }
        return actions.stream()
                .map(action -> HandoverItem.create(
                        action.actionId(),
                        action.title(),
                        action.status(),
                        action.projectTag(),
                        action.actionType(),
                        action.deadline(),
                        action.actionCreatedAt(),
                        action.sourceMeetingId(),
                        action.sourceMeetingTitle(),
                        action.content(),
                        action.parentActionTitle(),
                        action.startDate(),
                        !HandoverActionStatus.isComplete(action.status())
                ))
                .toList();
    }

    private Handover findHandover(Long handoverId) {
        return handoverRepository.findById(handoverId)
                .orElseThrow(() -> new BusinessException(HandoverErrorCode.HO_NOT_FOUND));
    }

    private void assertCanApprove(Handover handover, AuthPrincipal principal) {
        assertAuthenticated(principal);
        if (hasCompanyScope(principal)) {
            assertWriterInCompany(handover, principal.companyId());
            return;
        }
        if ("LEADER".equals(principal.authority())
                && Objects.equals(handover.getTeamId(), principal.teamId())
                && Objects.equals(orgQueryPort().findTeamLeaderId(handover.getTeamId()), principal.memberId())) {
            return;
        }
        throw new BusinessException(HandoverErrorCode.HO_ACCESS_DENIED);
    }

    private void assertCompanyApprover(Handover handover, AuthPrincipal principal) {
        assertAuthenticated(principal);
        if (!hasCompanyScope(principal)) {
            throw new BusinessException(HandoverErrorCode.HO_ACCESS_DENIED);
        }
        assertWriterInCompany(handover, principal.companyId());
    }

    private void assertWriterInCompany(Handover handover, Long companyId) {
        if (companyId == null
                || !orgQueryPort().findMemberIdsByCompany(companyId).contains(handover.getWriterMemberId())) {
            throw new BusinessException(HandoverErrorCode.HO_ACCESS_DENIED);
        }
    }

    private void assertAuthenticated(AuthPrincipal principal) {
        if (principal == null || principal.memberId() == null) {
            throw new BusinessException(HandoverErrorCode.HO_ACCESS_DENIED);
        }
    }

    private boolean hasCompanyScope(AuthPrincipal principal) {
        return principal.isAdmin() || "OWNER".equals(principal.authority());
    }

    private ActionReassignPort actionReassignPort() {
        if (actionReassignPort == null) {
            throw new BusinessException(HandoverErrorCode.HO_CONFLICT);
        }
        return actionReassignPort;
    }

    private OrgQueryPort orgQueryPort() {
        if (orgQueryPort == null) {
            throw new BusinessException(HandoverErrorCode.HO_CONFLICT);
        }
        return orgQueryPort;
    }

    private MemberStatusPort memberStatusPort() {
        if (memberStatusPort == null) {
            throw new BusinessException(HandoverErrorCode.HO_CONFLICT);
        }
        return memberStatusPort;
    }
}
