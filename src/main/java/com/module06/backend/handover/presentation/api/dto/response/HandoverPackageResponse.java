package com.module06.backend.handover.presentation.api.dto.response;

import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase;
import com.module06.backend.handover.domain.model.HandoverType;
import java.time.LocalDate;
import java.util.List;

public record HandoverPackageResponse(
        BasicInfo basicInfo,
        GapSummary gapSummary,
        List<Item> items,
        List<ContextCard> contextCards,
        List<MeetingHistory> meetingHistories,
        List<ReassigneeGroup> reassigneeGroups
) {

    public static HandoverPackageResponse from(GetHandoverPackageUseCase.HandoverPackage handoverPackage) {
        return new HandoverPackageResponse(
                BasicInfo.from(handoverPackage.basicInfo()),
                GapSummary.from(handoverPackage.gapSummary()),
                handoverPackage.items().stream().map(Item::from).toList(),
                handoverPackage.contextCards().stream().map(ContextCard::from).toList(),
                handoverPackage.meetingHistories().stream().map(MeetingHistory::from).toList(),
                handoverPackage.reassigneeGroups().stream().map(ReassigneeGroup::from).toList()
        );
    }

    public record BasicInfo(
            String writerName,
            String writerPosition,
            Long teamId,
            HandoverType absenceType,
            LocalDate startDate,
            LocalDate returnDate,
            LocalDate lastWorkingDay
    ) {

        static BasicInfo from(GetHandoverPackageUseCase.BasicInfo basicInfo) {
            return new BasicInfo(
                    basicInfo.writerName(),
                    basicInfo.writerPosition(),
                    basicInfo.teamId(),
                    basicInfo.absenceType(),
                    basicInfo.startDate(),
                    basicInfo.returnDate(),
                    basicInfo.lastWorkingDay()
            );
        }
    }

    public record GapSummary(int totalItems, int incompleteCount, int dueSoonCount) {

        static GapSummary from(GetHandoverPackageUseCase.GapSummary gapSummary) {
            return new GapSummary(gapSummary.totalItems(), gapSummary.incompleteCount(), gapSummary.dueSoonCount());
        }
    }

    public record Item(
            Long actionId,
            String title,
            String status,
            LocalDate deadline,
            String projectTag,
            String sourceMeetingTitle
    ) {

        static Item from(GetHandoverPackageUseCase.Item item) {
            return new Item(
                    item.actionId(),
                    item.title(),
                    item.status(),
                    item.deadline(),
                    item.projectTag(),
                    item.sourceMeetingTitle()
            );
        }
    }

    public record ContextCard(Long actionId, String title, String contentSnap) {

        static ContextCard from(GetHandoverPackageUseCase.ContextCard contextCard) {
            return new ContextCard(contextCard.actionId(), contextCard.title(), contextCard.contentSnap());
        }
    }

    public record MeetingHistory(
            Long meetingId,
            LocalDate date,
            List<String> attendees,
            String decisionSummary,
            String actionItemsSummary
    ) {

        static MeetingHistory from(GetHandoverPackageUseCase.MeetingHistory meetingHistory) {
            return new MeetingHistory(
                    meetingHistory.meetingId(),
                    meetingHistory.date(),
                    meetingHistory.attendees(),
                    meetingHistory.decisionSummary(),
                    meetingHistory.actionItemsSummary()
            );
        }
    }

    public record ReassigneeGroup(Long reassigneeId, String reassigneeName, List<Item> items) {

        static ReassigneeGroup from(GetHandoverPackageUseCase.ReassigneeGroup reassigneeGroup) {
            return new ReassigneeGroup(
                    reassigneeGroup.reassigneeId(),
                    reassigneeGroup.reassigneeName(),
                    reassigneeGroup.items().stream().map(Item::from).toList()
            );
        }
    }
}
