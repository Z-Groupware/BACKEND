package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.util.List;

public interface GetHandoverPackageUseCase {

    HandoverPackage getPackage(Long handoverId, LocalDate referenceDate);

    record HandoverPackage(
            BasicInfo basicInfo,
            GapSummary gapSummary,
            List<Item> items,
            List<ContextCard> contextCards,
            List<MeetingHistory> meetingHistories,
            List<ReassigneeGroup> reassigneeGroups
    ) {
    }

    record BasicInfo(
            String writerName,
            String writerPosition,
            Long teamId,
            HandoverType absenceType,
            LocalDate startDate,
            LocalDate returnDate,
            LocalDate lastWorkingDay
    ) {
    }

    record GapSummary(
            int totalItems,
            int incompleteCount,
            int dueSoonCount
    ) {
    }

    record Item(
            Long actionId,
            String title,
            String status,
            LocalDate deadline,
            String projectTag,
            String sourceMeetingTitle
    ) {
    }

    record ContextCard(
            Long actionId,
            String title,
            String contentSnap
    ) {
    }

    record MeetingHistory(
            Long meetingId,
            LocalDate date,
            List<String> attendees,
            String decisionSummary,
            String actionItemsSummary
    ) {
    }

    record ReassigneeGroup(
            Long reassigneeId,
            String reassigneeName,
            List<Item> items
    ) {
    }
}
