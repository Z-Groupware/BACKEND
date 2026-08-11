package com.module06.backend.handover.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Handover {

    private final Long id;
    private final Long writerMemberId;
    private final Long teamId;
    private final String teamNameSnap;
    private final HandoverType handoverType;
    private HandoverStatus status;
    private final LocalDateTime leaveStartAt;
    private final LocalDateTime leaveEndAt;
    private final LocalDate lastWorkingDay;
    private final String writerNameSnap;
    private final String writerPositionSnap;
    private final String note;
    private Long intermediateApproverId;
    private String intermediateApproverNameSnap;
    private LocalDateTime intermediateApprovedAt;
    private String rejectReason;
    private LocalDateTime finalizedAt;
    private Long finalApproverId;
    private String finalApproverNameSnap;
    private final Long version;
    private final List<HandoverItem> items;

    private Handover(Long id, Long writerMemberId, Long teamId, String teamNameSnap, HandoverType handoverType,
                     HandoverStatus status, LocalDateTime leaveStartAt, LocalDateTime leaveEndAt,
                     LocalDate lastWorkingDay, String writerNameSnap, String writerPositionSnap,
                     String note, Long intermediateApproverId, String intermediateApproverNameSnap,
                     LocalDateTime intermediateApprovedAt, String rejectReason, LocalDateTime finalizedAt,
                     Long finalApproverId, String finalApproverNameSnap, Long version,
                     List<HandoverItem> items) {
        requireId(writerMemberId, "writerMemberId");
        requireId(teamId, "teamId");
        if (handoverType == null) {
            throw new BusinessException(HandoverErrorCode.HO_TYPE_REQUIRED);
        }
        if (status == null) {
            throw new BusinessException(HandoverErrorCode.HO_STATUS_REQUIRED);
        }
        requireText(writerNameSnap, "writerNameSnap");
        requireText(writerPositionSnap, "writerPositionSnap");
        this.id = id;
        this.writerMemberId = writerMemberId;
        this.teamId = teamId;
        this.teamNameSnap = teamNameSnap;
        this.handoverType = handoverType;
        this.status = status;
        this.leaveStartAt = leaveStartAt;
        this.leaveEndAt = leaveEndAt;
        this.lastWorkingDay = lastWorkingDay;
        this.writerNameSnap = writerNameSnap;
        this.writerPositionSnap = writerPositionSnap;
        this.note = note;
        this.intermediateApproverId = intermediateApproverId;
        this.intermediateApproverNameSnap = intermediateApproverNameSnap;
        this.intermediateApprovedAt = intermediateApprovedAt;
        this.rejectReason = rejectReason;
        this.finalizedAt = finalizedAt;
        this.finalApproverId = finalApproverId;
        this.finalApproverNameSnap = finalApproverNameSnap;
        this.version = version;
        this.items = new ArrayList<>(items == null ? List.of() : items);
        validatePeriod();
    }

    public static Handover createVacation(Long writerMemberId, Long teamId, String writerNameSnap,
                                          String writerPositionSnap, LocalDateTime leaveStartAt,
                                          LocalDateTime leaveEndAt, List<HandoverItem> items) {
        return createVacation(writerMemberId, teamId, null, writerNameSnap, writerPositionSnap, null, leaveStartAt,
                leaveEndAt, items);
    }

    public static Handover createVacation(Long writerMemberId, Long teamId, String teamNameSnap,
                                          String writerNameSnap, String writerPositionSnap, String note,
                                          LocalDateTime leaveStartAt, LocalDateTime leaveEndAt,
                                          List<HandoverItem> items) {
        return new Handover(null, writerMemberId, teamId, teamNameSnap, HandoverType.VACATION,
                HandoverStatus.SUBMITTED,
                leaveStartAt, leaveEndAt, null, writerNameSnap, writerPositionSnap,
                note, null, null, null, null, null, null, null, null, items);
    }

    public static Handover createOffboarding(Long writerMemberId, Long teamId, String writerNameSnap,
                                             String writerPositionSnap, LocalDate lastWorkingDay,
                                             List<HandoverItem> items) {
        return createOffboarding(writerMemberId, teamId, null, writerNameSnap, writerPositionSnap, null,
                lastWorkingDay, items);
    }

    public static Handover createOffboarding(Long writerMemberId, Long teamId, String teamNameSnap,
                                             String writerNameSnap, String writerPositionSnap, String note,
                                             LocalDate lastWorkingDay, List<HandoverItem> items) {
        return new Handover(null, writerMemberId, teamId, teamNameSnap, HandoverType.OFFBOARDING,
                HandoverStatus.SUBMITTED,
                null, null, lastWorkingDay, writerNameSnap, writerPositionSnap,
                note, null, null, null, null, null, null, null, null, items);
    }

    public static Handover restore(Long id, Long writerMemberId, Long teamId, HandoverType handoverType,
                                   HandoverStatus status, LocalDateTime leaveStartAt, LocalDateTime leaveEndAt,
                                   LocalDate lastWorkingDay, String writerNameSnap, String writerPositionSnap,
                                   Long intermediateApproverId, String intermediateApproverNameSnap,
                                   LocalDateTime intermediateApprovedAt, String rejectReason,
                                   LocalDateTime finalizedAt, Long finalApproverId, String finalApproverNameSnap,
                                   Long version, List<HandoverItem> items) {
        return restore(id, writerMemberId, teamId, null, handoverType, status, leaveStartAt, leaveEndAt,
                lastWorkingDay, writerNameSnap, writerPositionSnap, null, intermediateApproverId,
                intermediateApproverNameSnap, intermediateApprovedAt, rejectReason, finalizedAt,
                finalApproverId, finalApproverNameSnap, version, items);
    }

    public static Handover restore(Long id, Long writerMemberId, Long teamId, String teamNameSnap,
                                   HandoverType handoverType, HandoverStatus status,
                                   LocalDateTime leaveStartAt, LocalDateTime leaveEndAt,
                                   LocalDate lastWorkingDay, String writerNameSnap, String writerPositionSnap,
                                   String note, Long intermediateApproverId, String intermediateApproverNameSnap,
                                   LocalDateTime intermediateApprovedAt, String rejectReason,
                                   LocalDateTime finalizedAt, Long finalApproverId, String finalApproverNameSnap,
                                   Long version, List<HandoverItem> items) {
        return new Handover(id, writerMemberId, teamId, teamNameSnap, handoverType, status, leaveStartAt, leaveEndAt, lastWorkingDay,
                writerNameSnap, writerPositionSnap, note, intermediateApproverId, intermediateApproverNameSnap,
                intermediateApprovedAt, rejectReason, finalizedAt, finalApproverId, finalApproverNameSnap, version,
                items);
    }

    public void reassignItem(Long actionId, Long toMemberId, String reassigneeNameSnap,
                             String reassigneePositionSnap, LocalDateTime at) {
        if (status != HandoverStatus.SUBMITTED) {
            throw new BusinessException(HandoverErrorCode.HO_REASSIGN_NOT_ALLOWED);
        }
        findItem(actionId).reassignTo(toMemberId, reassigneeNameSnap, reassigneePositionSnap, at);
    }

    public void complete(Long approverId, String approverNameSnap, LocalDateTime at) {
        if (status != HandoverStatus.SUBMITTED) {
            throw new BusinessException(HandoverErrorCode.HO_COMPLETE_NOT_ALLOWED);
        }
        requireId(approverId, "approverId");
        requireText(approverNameSnap, "approverNameSnap");
        if (at == null) {
            throw new BusinessException(HandoverErrorCode.HO_APPROVED_AT_REQUIRED);
        }
        if (!isAllReassigned()) {
            throw new BusinessException(HandoverErrorCode.HO_ITEMS_NOT_FULLY_REASSIGNED);
        }
        status = HandoverStatus.REASSIGNED;
        intermediateApproverId = approverId;
        intermediateApproverNameSnap = approverNameSnap;
        intermediateApprovedAt = at;
    }

    public void finalizeApproval(Long approverId, String approverNameSnap, LocalDateTime at) {
        finalizeApproval(approverId, approverNameSnap, at, false);
    }

    public void finalizeApproval(Long approverId, String approverNameSnap, LocalDateTime at,
                                 boolean allowDirectOffboardingFinalize) {
        boolean directOffboardingFinalizeAllowed = allowDirectOffboardingFinalize
                && status == HandoverStatus.SUBMITTED
                && handoverType == HandoverType.OFFBOARDING;
        if (status != HandoverStatus.REASSIGNED && !directOffboardingFinalizeAllowed) {
            throw new BusinessException(HandoverErrorCode.HO_FINALIZE_NOT_ALLOWED);
        }
        requireId(approverId, "approverId");
        requireText(approverNameSnap, "approverNameSnap");
        if (at == null) {
            throw new BusinessException(HandoverErrorCode.HO_FINALIZED_AT_REQUIRED);
        }
        status = HandoverStatus.FINALIZED;
        finalizedAt = at;
        finalApproverId = approverId;
        finalApproverNameSnap = approverNameSnap;
    }

    public void reject(String reason) {
        // FINALIZED·REJECTED는 상태머신 종료 상태 — 재반려로 rejectReason을 덮어쓰지 못하게 막는다.
        if (status == HandoverStatus.FINALIZED || status == HandoverStatus.REJECTED) {
            throw new BusinessException(HandoverErrorCode.HO_REJECT_NOT_ALLOWED);
        }
        requireText(reason, "reason");
        status = HandoverStatus.REJECTED;
        rejectReason = reason;
    }

    public boolean isAllReassigned() {
        return items.stream()
                .filter(HandoverItem::isReassignRequired)
                .allMatch(HandoverItem::isReassigned);
    }

    /**
     * "귀속 대기" — 팀장 오프보딩이 후임 없이 직행 finalize(SUBMITTED→FINALIZED)돼, 문서·퇴사는 확정됐지만
     * 액션은 아직 새 팀장에게 귀속되지 않은 상태. 파생 판정이라 별도 상태값을 두지 않는다.
     * 일반 사원 오프보딩은 complete(전 항목 재분배)를 거쳐야 FINALIZED라 이 조건에 걸리지 않는다.
     */
    public boolean isPendingAttribution() {
        return status == HandoverStatus.FINALIZED
                && handoverType == HandoverType.OFFBOARDING
                && !isAllReassigned();
    }

    /**
     * 귀속 대기 상태의 인계 항목 전부를 신규 팀장 1명에게 일괄 귀속시킨다.
     * 상태는 이미 FINALIZED라 전이 없이 항목 스냅샷만 채운다(이관 후 isPendingAttribution=false).
     */
    public void attributeToNewLeader(Long newLeaderId, String reassigneeNameSnap,
                                     String reassigneePositionSnap, LocalDateTime at) {
        if (!isPendingAttribution()) {
            throw new BusinessException(HandoverErrorCode.HO_ATTRIBUTE_NOT_ALLOWED);
        }
        requireId(newLeaderId, "newLeaderId");
        requireText(reassigneeNameSnap, "reassigneeNameSnap");
        requireText(reassigneePositionSnap, "reassigneePositionSnap");
        if (at == null) {
            throw new BusinessException(HandoverErrorCode.HO_REASSIGNED_AT_REQUIRED);
        }
        items.stream()
                .filter(HandoverItem::isReassignRequired)
                .filter(item -> !item.isReassigned())
                .forEach(item -> {
                    item.reassignTo(newLeaderId, reassigneeNameSnap, reassigneePositionSnap, at);
                    item.commit(at);
                });
    }

    public List<HandoverItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private HandoverItem findItem(Long actionId) {
        return items.stream()
                .filter(item -> item.hasActionId(actionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(HandoverErrorCode.HO_ITEM_NOT_FOUND));
    }

    private void validatePeriod() {
        if (handoverType == HandoverType.VACATION) {
            if (lastWorkingDay != null) {
                throw new BusinessException(HandoverErrorCode.LV_LAST_WORKING_DAY_NOT_ALLOWED);
            }
            if (leaveStartAt == null || leaveEndAt == null) {
                throw new BusinessException(HandoverErrorCode.LV_VACATION_PERIOD_REQUIRED);
            }
            if (leaveEndAt.isBefore(leaveStartAt)) {
                throw new BusinessException(HandoverErrorCode.LV_VACATION_PERIOD_INVALID);
            }
        }
        if (handoverType == HandoverType.OFFBOARDING) {
            if (leaveStartAt != null || leaveEndAt != null) {
                throw new BusinessException(HandoverErrorCode.HO_OFFBOARDING_PERIOD_NOT_ALLOWED);
            }
            if (lastWorkingDay == null) {
                throw new BusinessException(HandoverErrorCode.HO_LAST_WORKING_DAY_REQUIRED);
            }
        }
    }

    private static void requireId(Long value, String name) {
        if (value == null) {
            throw new BusinessException(HandoverErrorCode.HO_REQUIRED_ID);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(HandoverErrorCode.HO_REQUIRED_TEXT);
        }
    }

    public Long getId() { return id; }
    public Long getWriterMemberId() { return writerMemberId; }
    public Long getTeamId() { return teamId; }
    public String getTeamNameSnap() { return teamNameSnap; }
    public HandoverType getHandoverType() { return handoverType; }
    public HandoverStatus getStatus() { return status; }
    public LocalDateTime getLeaveStartAt() { return leaveStartAt; }
    public LocalDateTime getLeaveEndAt() { return leaveEndAt; }
    public LocalDate getLastWorkingDay() { return lastWorkingDay; }
    public String getWriterNameSnap() { return writerNameSnap; }
    public String getWriterPositionSnap() { return writerPositionSnap; }
    public String getNote() { return note; }
    public Long getIntermediateApproverId() { return intermediateApproverId; }
    public String getIntermediateApproverNameSnap() { return intermediateApproverNameSnap; }
    public LocalDateTime getIntermediateApprovedAt() { return intermediateApprovedAt; }
    public String getRejectReason() { return rejectReason; }
    public LocalDateTime getFinalizedAt() { return finalizedAt; }
    public Long getFinalApproverId() { return finalApproverId; }
    public String getFinalApproverNameSnap() { return finalApproverNameSnap; }
    public Long getVersion() { return version; }
}
