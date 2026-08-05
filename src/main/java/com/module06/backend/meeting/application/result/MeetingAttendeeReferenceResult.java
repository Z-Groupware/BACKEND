package com.module06.backend.meeting.application.result;

/*
 * E 인수인계의 배치 오너십 판정에 제공할 회의와 참석자 식별자 쌍이다.
 */
public record MeetingAttendeeReferenceResult(Long meetingId, Long memberId) {
}
