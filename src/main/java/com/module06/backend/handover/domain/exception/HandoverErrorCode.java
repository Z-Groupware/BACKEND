package com.module06.backend.handover.domain.exception;

import com.module06.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum HandoverErrorCode implements ErrorCode {

    HO_CREATE_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "HO-001", "인수인계 생성 요청이 올바르지 않습니다."),
    HO_REASSIGN_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "HO-002", "인수인계 재배정 요청이 올바르지 않습니다."),
    HO_REJECT_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "HO-003", "인수인계 반려 요청이 올바르지 않습니다."),
    HO_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "HO-004", "인수인계 유형은 필수입니다."),
    HO_STATUS_REQUIRED(HttpStatus.BAD_REQUEST, "HO-005", "인수인계 상태는 필수입니다."),
    HO_REQUIRED_ID(HttpStatus.BAD_REQUEST, "HO-006", "필수 식별자가 누락되었습니다."),
    HO_REQUIRED_TEXT(HttpStatus.BAD_REQUEST, "HO-007", "필수 텍스트가 누락되었습니다."),
    HO_APPROVED_AT_REQUIRED(HttpStatus.BAD_REQUEST, "HO-008", "중간 승인 시각은 필수입니다."),
    HO_FINALIZED_AT_REQUIRED(HttpStatus.BAD_REQUEST, "HO-009", "최종 승인 시각은 필수입니다."),
    HO_REASSIGNED_AT_REQUIRED(HttpStatus.BAD_REQUEST, "HO-010", "재배정 시각은 필수입니다."),
    HO_LAST_WORKING_DAY_REQUIRED(HttpStatus.BAD_REQUEST, "HO-011", "퇴사 마지막 근무일은 필수입니다."),
    HO_OFFBOARDING_PERIOD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "HO-012", "퇴사 인수인계에는 휴직 기간을 입력할 수 없습니다."),
    HO_SELECTED_ACTION_NOT_HANDOVERABLE(HttpStatus.BAD_REQUEST, "HO-013", "선택한 액션은 인수인계할 수 없습니다."),
    HO_LIST_SCOPE_REQUIRED(HttpStatus.BAD_REQUEST, "HO-023", "목록 조회 스코프(writerMemberId 또는 teamId)가 필요합니다."),
    HO_LIST_SCOPE_AMBIGUOUS(HttpStatus.BAD_REQUEST, "HO-024", "목록 조회 스코프는 writerMemberId·teamId 중 하나만 지정해야 합니다."),
    HO_BULK_REASSIGN_TARGET_REQUIRED(HttpStatus.BAD_REQUEST, "HO-025", "일괄 이관 대상 인수자는 필수입니다."),
    LV_LAST_WORKING_DAY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "LV-001", "휴직 인수인계에는 마지막 근무일을 입력할 수 없습니다."),
    LV_VACATION_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "LV-002", "휴직 종료일은 시작일보다 빠를 수 없습니다."),
    LV_VACATION_PERIOD_REQUIRED(HttpStatus.BAD_REQUEST, "LV-003", "휴직 시작일과 종료일은 필수입니다."),

    HO_NOT_FOUND(HttpStatus.NOT_FOUND, "HO-014", "인수인계를 찾을 수 없습니다."),
    HO_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "HO-015", "인수인계 항목을 찾을 수 없습니다."),

    HO_ACTIVE_ALREADY_EXISTS(HttpStatus.CONFLICT, "HO-016", "이미 진행 중인 인수인계가 있습니다."),
    HO_COMPLETE_NOT_ALLOWED(HttpStatus.CONFLICT, "HO-017", "현재 상태에서는 인수인계를 완료할 수 없습니다."),
    HO_FINALIZE_NOT_ALLOWED(HttpStatus.CONFLICT, "HO-018", "현재 상태에서는 인수인계를 최종 승인할 수 없습니다."),
    HO_REASSIGN_NOT_ALLOWED(HttpStatus.CONFLICT, "HO-019", "현재 상태에서는 인수인계 항목을 재배정할 수 없습니다."),
    HO_REJECT_NOT_ALLOWED(HttpStatus.CONFLICT, "HO-020", "현재 상태에서는 인수인계를 반려할 수 없습니다."),
    HO_ITEMS_NOT_FULLY_REASSIGNED(HttpStatus.CONFLICT, "HO-021", "필수 인수인계 항목이 모두 재배정되지 않았습니다."),
    HO_CONFLICT(HttpStatus.CONFLICT, "HO-022", "인수인계 처리 중 충돌이 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
