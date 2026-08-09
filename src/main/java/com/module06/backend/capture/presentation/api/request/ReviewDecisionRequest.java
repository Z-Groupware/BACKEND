package com.module06.backend.capture.presentation.api.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import com.module06.backend.capture.application.usecase.ApplyReviewDecisionUseCase.ReviewDecisionCommand;
import com.module06.backend.capture.domain.model.RejectReason;
import com.module06.backend.capture.domain.model.ReviewDecision;

/*
 * RVW-02 요청이다. 명세의 필드명을 그대로 따른다.
 *
 * <h2>사유의 필수 여부를 여기서 검사하지 않는다</h2>
 * MODIFY·REJECT 에는 필수이고 CONFIRM 에는 붙을 수 없는데, 그건 **필드 하나만 보고는 알 수
 * 없는 규칙**이다(decision 과의 조합이다). @AssertTrue 로 넣으면 응답 메시지가 필드 오류로
 * 나가 어느 규칙을 어겼는지 흐려지고, 명세가 정한 코드(422 MEETING_422_3)를 줄 수 없다.
 * 서비스가 판정하고 DB CHECK 가 마지막으로 막는다.
 *
 * <h2>value 는 고친 칸만 담는다</h2>
 * null 은 "안 고쳤다"이고 "비우라"가 아니다. 담당자 지우기는 검토 화면에 없는 동작이고,
 * PERSONAL 액션은 담당자가 필수다.
 */
public record ReviewDecisionRequest(
        @NotNull(message = "판정(decision)은 필수입니다.")
        ReviewDecision decision,
        RejectReason rejectReason,
        ReviewValue value
) {

    public ReviewDecisionCommand toCommand(long companyId, long meetingId, long actionId, long confirmedBy) {
        return new ReviewDecisionCommand(
                companyId,
                meetingId,
                actionId,
                confirmedBy,
                decision,
                rejectReason,
                value != null ? value.assigneeMemberId() : null,
                value != null ? value.dueDate() : null);
    }

    /* 사람이 고친 값. 담당자·기한만 고칠 수 있다(명세 RVW-02 의 value). */
    public record ReviewValue(Long assigneeMemberId, LocalDate dueDate) {
    }
}
