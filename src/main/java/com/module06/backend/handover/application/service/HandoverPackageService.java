package com.module06.backend.handover.application.service;

import com.module06.backend.handover.application.port.out.MeetingQueryPort;
import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverActionStatus;
import com.module06.backend.handover.domain.model.HandoverItem;
import com.module06.backend.handover.domain.repository.HandoverRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class HandoverPackageService implements GetHandoverPackageUseCase {

    private static final Long UNASSIGNED_GROUP_ID = null;
    private static final String UNASSIGNED_GROUP_NAME = "미배정";

    private final HandoverRepository handoverRepository;
    private final MeetingQueryPort meetingQueryPort;

    public HandoverPackageService(HandoverRepository handoverRepository, MeetingQueryPort meetingQueryPort) {
        this.handoverRepository = handoverRepository;
        this.meetingQueryPort = meetingQueryPort;
    }

    @Autowired
    public HandoverPackageService(HandoverRepository handoverRepository,
                                  Optional<MeetingQueryPort> meetingQueryPort) {
        this(handoverRepository, meetingQueryPort.orElse(null));
    }

    @Override
    public HandoverPackage getPackage(Long handoverId, LocalDate referenceDate) {
        if (referenceDate == null) {
            throw new BusinessException(HandoverErrorCode.HO_REQUIRED_ID);
        }
        Handover handover = handoverRepository.findById(handoverId)
                .orElseThrow(() -> new BusinessException(HandoverErrorCode.HO_NOT_FOUND));

        List<HandoverItem> sourceItems = handover.getItems();
        List<Item> items = sourceItems.stream().map(this::toItem).toList();
        return new HandoverPackage(
                toBasicInfo(handover),
                toGapSummary(sourceItems, referenceDate),
                items,
                sourceItems.stream().map(this::toContextCard).toList(),
                toMeetingHistories(sourceItems),
                toReassigneeGroups(sourceItems)
        );
    }

    private BasicInfo toBasicInfo(Handover handover) {
        return new BasicInfo(
                handover.getWriterNameSnap(),
                handover.getWriterPositionSnap(),
                handover.getTeamId(),
                handover.getHandoverType(),
                handover.getLeaveStartAt(),
                handover.getLeaveEndAt(),
                handover.getLastWorkingDay(),
                handover.getNote()
        );
    }

    private GapSummary toGapSummary(List<HandoverItem> items, LocalDate referenceDate) {
        int incompleteCount = (int) items.stream().filter(item -> !isComplete(item)).count();
        int dueSoonCount = (int) items.stream()
                .filter(item -> !isComplete(item))
                .filter(item -> isDueSoon(item.getDeadlineSnap(), referenceDate))
                .count();
        return new GapSummary(items.size(), incompleteCount, dueSoonCount);
    }

    private boolean isComplete(HandoverItem item) {
        return HandoverActionStatus.isComplete(item.getActionStatusSnap());
    }

    private boolean isDueSoon(LocalDate deadline, LocalDate referenceDate) {
        return deadline != null
                && !deadline.isBefore(referenceDate)
                && !deadline.isAfter(referenceDate.plusDays(7));
    }

    private Item toItem(HandoverItem item) {
        return new Item(
                item.getActionId(),
                item.getActionTitleSnap(),
                item.getActionStatusSnap(),
                item.getDeadlineSnap(),
                item.getActionCreatedAtSnap() == null ? null : item.getActionCreatedAtSnap().toLocalDate(),
                item.getProjectTagSnap(),
                item.getSourceMeetingTitleSnap()
        );
    }

    private ContextCard toContextCard(HandoverItem item) {
        return new ContextCard(item.getActionId(), item.getActionTitleSnap(), item.getContentSnap());
    }

    private List<MeetingHistory> toMeetingHistories(List<HandoverItem> items) {
        return items.stream()
                .map(HandoverItem::getSourceMeetingId)
                .filter(Objects::nonNull)
                .distinct()
                .map(meetingQueryPort()::findMeeting)
                .map(history -> new MeetingHistory(
                        history.meetingId(),
                        history.date(),
                        history.attendees(),
                        history.decisionSummary(),
                        history.actionItemsSummary()
                ))
                .toList();
    }

    private List<ReassigneeGroup> toReassigneeGroups(List<HandoverItem> items) {
        Map<AssigneeKey, List<HandoverItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(this::toAssigneeKey, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream()
                .map(entry -> new ReassigneeGroup(
                        entry.getKey().reassigneeId(),
                        entry.getKey().reassigneeName(),
                        entry.getValue().stream().map(this::toItem).toList()
                ))
                .toList();
    }

    private AssigneeKey toAssigneeKey(HandoverItem item) {
        if (item.getReassigneeId() == null) {
            return new AssigneeKey(UNASSIGNED_GROUP_ID, UNASSIGNED_GROUP_NAME);
        }
        return new AssigneeKey(item.getReassigneeId(), item.getReassigneeNameSnap());
    }

    private record AssigneeKey(Long reassigneeId, String reassigneeName) {
    }

    private MeetingQueryPort meetingQueryPort() {
        if (meetingQueryPort == null) {
            throw new BusinessException(HandoverErrorCode.HO_CONFLICT);
        }
        return meetingQueryPort;
    }
}
