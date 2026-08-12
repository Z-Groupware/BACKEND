package com.module06.backend.meeting.domain.repository;

import com.module06.backend.meeting.domain.model.MeetingAgenda;

/*
 * 회의 개설 트랜잭션에서 안건 계층을 저장하는 도메인 저장 계약이다.
 */
public interface MeetingTopicRepository {

    /* 저장된 회의 아래에 MAIN 한 건과 이를 부모로 하는 SUB 목록을 저장한다. */
    void saveAgenda(Long meetingId, MeetingAgenda agenda);
}
