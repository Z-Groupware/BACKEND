package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
            LocalDateTime startDate,
            LocalDateTime returnDate,
            LocalDate lastWorkingDay,
            String note
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
            LocalDate startAt,   // 타임라인 막대 시작점 = 액션 생성일(스냅샷). null이면 FE가 축 시작으로 대체.
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
