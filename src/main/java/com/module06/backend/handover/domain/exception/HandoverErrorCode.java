package com.module06.backend.handover.domain.exception;

import com.module06.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum HandoverErrorCode implements ErrorCode {

    HO_CREATE_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "HO-001", "Handover create command is invalid."),
    HO_REASSIGN_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "HO-002", "Handover reassign command is invalid."),
    HO_REJECT_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "HO-003", "Handover reject command is invalid."),
    HO_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "HO-004", "Handover type is required."),
    HO_STATUS_REQUIRED(HttpStatus.BAD_REQUEST, "HO-005", "Handover status is required."),
    HO_REQUIRED_ID(HttpStatus.BAD_REQUEST, "HO-006", "Required id is missing."),
    HO_REQUIRED_TEXT(HttpStatus.BAD_REQUEST, "HO-007", "Required text is missing."),
    HO_APPROVED_AT_REQUIRED(HttpStatus.BAD_REQUEST, "HO-008", "Intermediate approval time is required."),
    HO_FINALIZED_AT_REQUIRED(HttpStatus.BAD_REQUEST, "HO-009", "Final approval time is required."),
    HO_REASSIGNED_AT_REQUIRED(HttpStatus.BAD_REQUEST, "HO-010", "Reassignment time is required."),
    HO_LAST_WORKING_DAY_REQUIRED(HttpStatus.BAD_REQUEST, "HO-011", "Last working day is required."),
    HO_OFFBOARDING_PERIOD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "HO-012", "Offboarding handover cannot have a leave period."),
    HO_SELECTED_ACTION_NOT_HANDOVERABLE(HttpStatus.BAD_REQUEST, "HO-013", "Selected action is not handoverable."),

    HO_NOT_FOUND(HttpStatus.NOT_FOUND, "HO-014", "Handover was not found."),
    HO_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "HO-015", "Handover item was not found."),

    HO_ACTIVE_ALREADY_EXISTS(HttpStatus.CONFLICT, "HO-016", "Active handover already exists."),
    HO_COMPLETE_NOT_ALLOWED(HttpStatus.CONFLICT, "HO-017", "Handover cannot be completed in the current status."),
    HO_FINALIZE_NOT_ALLOWED(HttpStatus.CONFLICT, "HO-018", "Handover cannot be finalized in the current status."),
    HO_REASSIGN_NOT_ALLOWED(HttpStatus.CONFLICT, "HO-019", "Handover item cannot be reassigned in the current status."),
    HO_REJECT_NOT_ALLOWED(HttpStatus.CONFLICT, "HO-020", "Handover cannot be rejected in the current status."),
    HO_ITEMS_NOT_FULLY_REASSIGNED(HttpStatus.CONFLICT, "HO-021", "Required handover items are not fully reassigned."),
    HO_CONFLICT(HttpStatus.CONFLICT, "HO-022", "Handover processing conflict occurred."),
    HO_LIST_SCOPE_REQUIRED(HttpStatus.BAD_REQUEST, "HO-023", "Handover list scope is required."),
    HO_LIST_SCOPE_AMBIGUOUS(HttpStatus.BAD_REQUEST, "HO-024", "Only one handover list scope can be specified."),
    HO_BULK_REASSIGN_TARGET_REQUIRED(HttpStatus.BAD_REQUEST, "HO-025", "Bulk reassignment successor is required."),

    LV_LAST_WORKING_DAY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "LV-001", "Vacation handover cannot have last working day."),
    LV_VACATION_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "LV-002", "Vacation end date cannot be before start date."),
    LV_VACATION_PERIOD_REQUIRED(HttpStatus.BAD_REQUEST, "LV-003", "Vacation start and end dates are required.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
