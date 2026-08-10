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
        OrgQueryPort.MemberSnapshot writer = orgQueryPort().findMember(command.writerMemberId());
        List<HandoverItem> items = snapshotItems(command);
        Handover handover = command.handoverType() == HandoverType.VACATION
                ? Handover.createVacation(command.writerMemberId(), command.teamId(), writer.name(), writer.position(),
                        command.leaveStartAt(), command.leaveEndAt(), items)
                : Handover.createOffboarding(command.writerMemberId(), command.teamId(), writer.name(), writer.position(),
                        command.lastWorkingDay(), isLeaderOffboarding(command), items);
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
        OrgQueryPort.MemberSnapshot reassignee = orgQueryPort().findMember(command.toMemberId());
        handover.reassignItem(command.actionId(), command.toMemberId(), reassignee.name(), reassignee.position(),
                command.reassignedAt());
        return handoverRepository.save(handover);
    }

    @Override
    public Handover complete(Long handoverId, Long leaderId, LocalDateTime approvedAt) {
        Handover handover = findHandover(handoverId);
        OrgQueryPort.MemberSnapshot approver = orgQueryPort().findMember(leaderId);
        handover.complete(leaderId, approver.name(), approvedAt);
        handover.getItems().stream()
                .filter(HandoverItem::isReassignRequired)
                .forEach(item -> actionReassignPort().reassign(
                        item.getActionId(),
                        handover.getWriterMemberId(),
                        item.getReassigneeId()
                ));
        return handoverRepository.save(handover);
    }

    @Override
    public Handover finalize(Long handoverId, Long approverId, String approverName, LocalDateTime finalizedAt) {
        Handover handover = findHandover(handoverId);
        if (handover.getStatus() == HandoverStatus.SUBMITTED
                && handover.getHandoverType() == HandoverType.OFFBOARDING
                && handover.isLeaderHandover()) {
            handover.finalizeAsPendingAttribution(approverId, approverName, finalizedAt);
            memberStatusPort().offboard(handover.getWriterMemberId());
            finalizeHandoverInsightsUseCase.finalizeInsights(
                    new FinalizeHandoverInsightsCommand(handoverId, handover.getWriterMemberId()));
            return handoverRepository.save(handover);
        }

        handover.finalizeApproval(approverId, approverName, finalizedAt);
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
        Handover handover = findHandover(command.handoverId());
        OrgQueryPort.MemberSnapshot newLeader = orgQueryPort().findMember(command.newLeaderId());
        handover.attributeToNewLeader(command.newLeaderId(), newLeader.name(), newLeader.position(),
                command.attributedAt());
        handover.getItems().stream()
                .filter(HandoverItem::isReassignRequired)
                .forEach(item -> actionReassignPort().reassign(
                        item.getActionId(),
                        handover.getWriterMemberId(),
                        command.newLeaderId()
                ));
        return handoverRepository.save(handover);
    }

    @Override
    public Handover reject(RejectHandoverCommand command) {
        if (command == null) {
            throw new BusinessException(HandoverErrorCode.HO_REJECT_COMMAND_INVALID);
        }
        Handover handover = findHandover(command.handoverId());
        handover.reject(command.reason());
        memberStatusPort().restoreActive(handover.getWriterMemberId());
        return handoverRepository.save(handover);
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
                        action.sourceMeetingId(),
                        action.sourceMeetingTitle(),
                        action.content(),
                        !HandoverActionStatus.isComplete(action.status())
                ))
                .toList();
    }

    private boolean isLeaderOffboarding(CreateHandoverCommand command) {
        return command.handoverType() == HandoverType.OFFBOARDING
                && Objects.equals(orgQueryPort().findTeamLeaderId(command.teamId()), command.writerMemberId());
    }

    private Handover findHandover(Long handoverId) {
        return handoverRepository.findById(handoverId)
                .orElseThrow(() -> new BusinessException(HandoverErrorCode.HO_NOT_FOUND));
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
