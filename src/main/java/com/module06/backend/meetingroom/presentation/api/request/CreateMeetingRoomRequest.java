package com.module06.backend.meetingroom.presentation.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.module06.backend.meetingroom.application.command.CreateMeetingRoomCommand;

/*
 * ROOM-03 회의실 등록 요청 본문이며 모든 회의실은 24시간 예약 가능하다.
 */
public record CreateMeetingRoomRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 150) String location
) {

    /* 인증 회사 식별자와 요청 본문을 애플리케이션 명령으로 변환한다. */
    public CreateMeetingRoomCommand toCommand(Long companyId) {
        /* 회사 식별자는 요청 본문이 아닌 인증 principal 값만 사용한다. */
        return new CreateMeetingRoomCommand(
                companyId,
                name,
                location
        );
    }
}
