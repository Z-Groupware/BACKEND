package com.module06.backend.meeting.presentation.api.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.module06.backend.meeting.application.command.ReplaceMeetingAttendeesCommand;

/*
 * MEET-09 참석자 전체 교체 요청 본문이다.
 *
 * 개설자는 요청 목록에 없어도 서비스에서 첫 번째 참석자로 자동 포함한다.
 */
public record ReplaceMeetingAttendeesRequest(
        /* 개설자를 제외하고 교체할 전체 참석자 식별자 목록이며 빈 목록은 허용한다. */
        @NotNull List<@NotNull @Positive Long> attendeeMemberIds
) {

    /* 요청 목록이 DTO 생성 이후 변경되지 않도록 불변 복사한다. */
    public ReplaceMeetingAttendeesRequest {
        /* null은 Bean Validation이 처리하고 실제 목록만 방어적으로 복사한다. */
        if (attendeeMemberIds != null) {
            attendeeMemberIds = List.copyOf(attendeeMemberIds);
        }
    }

    /* 인증 정보와 Path 식별자를 요청 명단에 결합해 MEET-09 Command를 만든다. */
    public ReplaceMeetingAttendeesCommand toCommand(
            Long companyId,
            Long requesterMemberId,
            String requesterRole,
            boolean requesterAdmin,
            Long meetingId
    ) {
        /* 조작 불가능한 인증 정보는 Controller 인자로만 받아 Command에 전달한다. */
        return new ReplaceMeetingAttendeesCommand(
                companyId,
                requesterMemberId,
                requesterRole,
                requesterAdmin,
                meetingId,
                attendeeMemberIds
        );
    }
}
