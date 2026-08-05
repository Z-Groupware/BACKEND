package com.module06.backend.meeting.application.result;

import com.module06.backend.meeting.domain.model.MeetingTopicType;

/*
 * E 인수인계의 회의 맥락 타임라인에 제공할 D도메인 소유 주제 조회 결과다.
 *
 * 회의별 MAIN·SUB 구분과 화면 순서를 전달하되 영속성 엔티티는 외부 도메인에 노출하지 않는다.
 */
public record MeetingTopicResult(
        Long meetingId,
        Long topicId,
        Long parentTopicId,
        MeetingTopicType type,
        String content,
        int sortOrder
) {
}
