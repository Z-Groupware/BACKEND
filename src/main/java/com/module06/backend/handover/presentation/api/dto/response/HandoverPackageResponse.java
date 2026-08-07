package com.module06.backend.handover.presentation.api.dto.response;

import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase.BasicInfo;
import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase.ContextCard;
import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase.GapSummary;
import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase.HandoverPackage;
import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase.Item;
import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase.MeetingHistory;
import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase.ReassigneeGroup;
import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 인수인계 상세/패키지 응답. FE 인수인계 상세 화면(액션 타임라인 · 인계 패키지 · 회의 맥락)의 데이터 소스.
 * 팀의 GET /{id}(단순 aggregate=HandoverResponse)와 별개로 GET /{id}/package에서 이 리치 뷰를 제공한다.
 * application의 {@link HandoverPackage} 읽기 모델을 presentation 계층에 고정해 그대로 노출한다.
 */
public record HandoverPackageResponse(
        BasicInfoResponse basicInfo,
        GapSummaryResponse gapSummary,
        List<ItemResponse> items,
        List<ContextCardResponse> contextCards,
        List<MeetingHistoryResponse> meetingHistories,
        List<ReassigneeGroupResponse> reassigneeGroups
) {

    public static HandoverPackageResponse from(HandoverPackage p) {
        return new HandoverPackageResponse(
                BasicInfoResponse.from(p.basicInfo()),
                GapSummaryResponse.from(p.gapSummary()),
                p.items().stream().map(ItemResponse::from).toList(),
                p.contextCards().stream().map(ContextCardResponse::from).toList(),
                p.meetingHistories().stream().map(MeetingHistoryResponse::from).toList(),
                p.reassigneeGroups().stream().map(ReassigneeGroupResponse::from).toList()
        );
    }

    public record BasicInfoResponse(
            String writerName,
            String writerPosition,
            Long teamId,
            HandoverType absenceType,
            LocalDateTime startDate,
            LocalDateTime returnDate,
            LocalDate lastWorkingDay,
            String note
    ) {
        static BasicInfoResponse from(BasicInfo b) {
            return new BasicInfoResponse(b.writerName(), b.writerPosition(), b.teamId(), b.absenceType(),
                    b.startDate(), b.returnDate(), b.lastWorkingDay(), b.note());
        }
    }

    public record GapSummaryResponse(int totalItems, int incompleteCount, int dueSoonCount) {
        static GapSummaryResponse from(GapSummary g) {
            return new GapSummaryResponse(g.totalItems(), g.incompleteCount(), g.dueSoonCount());
        }
    }

    public record ItemResponse(
            Long actionId,
            String title,
            String status,
            LocalDate deadline,
            LocalDate startAt,
            String projectTag,
            String sourceMeetingTitle
    ) {
        static ItemResponse from(Item i) {
            return new ItemResponse(i.actionId(), i.title(), i.status(), i.deadline(), i.startAt(), i.projectTag(),
                    i.sourceMeetingTitle());
        }
    }

    public record ContextCardResponse(Long actionId, String title, String contentSnap) {
        static ContextCardResponse from(ContextCard c) {
            return new ContextCardResponse(c.actionId(), c.title(), c.contentSnap());
        }
    }

    public record MeetingHistoryResponse(
            Long meetingId,
            LocalDate date,
            List<String> attendees,
            String decisionSummary,
            String actionItemsSummary
    ) {
        static MeetingHistoryResponse from(MeetingHistory m) {
            return new MeetingHistoryResponse(m.meetingId(), m.date(), m.attendees(), m.decisionSummary(),
                    m.actionItemsSummary());
        }
    }

    public record ReassigneeGroupResponse(Long reassigneeId, String reassigneeName, List<ItemResponse> items) {
        static ReassigneeGroupResponse from(ReassigneeGroup g) {
            return new ReassigneeGroupResponse(g.reassigneeId(), g.reassigneeName(),
                    g.items().stream().map(ItemResponse::from).toList());
        }
    }
}
