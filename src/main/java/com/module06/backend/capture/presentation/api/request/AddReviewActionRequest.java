package com.module06.backend.capture.presentation.api.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.module06.backend.capture.application.usecase.AddReviewActionUseCase.AddReviewActionCommand;

/*
 * RVW-03 요청이다. 명세의 필드명을 그대로 따른다.
 *
 * <h2>담당자·기한에 @NotNull 을 붙이지 않는다</h2>
 * 둘 다 필수인데도 서비스에서 판정한다. 명세가 요구하는 코드가 422(MEETING_422_5)이고,
 * 빈 칸 검증으로 막으면 그 코드 대신 필드 오류 형식으로 나가 화면이 다른 분기를 타게 된다.
 * RVW-02 가 사유 필수를 서비스에서 보는 것과 같은 판단이다.
 *
 * <h2>title 은 여기서 막는다</h2>
 * 조합 규칙이 아니라 값 자체의 문제이고(빈 제목), action.title 이 VARCHAR(200) NOT NULL 이라
 * 길이를 넘기면 DB 까지 내려가 500 이 된다.
 */
public record AddReviewActionRequest(
        Long assigneeMemberId,

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
        String title,

        String detail,
        LocalDate dueDate,
        Long evidenceTranscriptId
) {

    public AddReviewActionCommand toCommand(long companyId, long meetingId, long requestedBy) {
        return new AddReviewActionCommand(
                companyId, meetingId, assigneeMemberId, title, detail, dueDate,
                evidenceTranscriptId, requestedBy);
    }
}
