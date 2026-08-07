package com.module06.backend.meeting.application.command;

import java.time.LocalDateTime;

/*
 * MEET-05 회의 부분 수정에 필요한 인증·경로·필드 존재 정보를 묶은 Command다.
 *
 * PATCH는 미전달과 명시적 null을 구분해야 하므로 각 값과 provided 플래그를 함께 보관한다.
 * 회사와 요청자 권한은 조작 가능한 본문이 아니라 Access Token principal에서만 채운다.
 */
public record UpdateMeetingCommand(
        Long companyId,
        Long requesterMemberId,
        String requesterRole,
        boolean requesterAdmin,
        Long meetingId,
        boolean titleProvided,
        String title,
        boolean projectIdProvided,
        Long projectId,
        boolean meetingRoomIdProvided,
        Long meetingRoomId,
        boolean startAtProvided,
        LocalDateTime startAt,
        boolean endAtProvided,
        LocalDateTime endAt,
        boolean recordingConsentProvided,
        Boolean recordingConsent
) {

    /* 수정 가능한 여섯 필드 중 하나라도 JSON 본문에 포함됐는지 확인한다. */
    public boolean hasAnyChange() {
        /* 모든 존재 플래그가 false면 의미 없는 PATCH 요청이다. */
        return titleProvided
                || projectIdProvided
                || meetingRoomIdProvided
                || startAtProvided
                || endAtProvided
                || recordingConsentProvided;
    }
}
