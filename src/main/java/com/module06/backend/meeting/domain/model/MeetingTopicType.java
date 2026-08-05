package com.module06.backend.meeting.domain.model;

/*
 * 회의 안건의 계층 유형이다.
 *
 * MAIN은 대주제, SUB는 대주제 아래의 소주제를 의미한다.
 */
public enum MeetingTopicType {
    /* 회의의 대주제다. */
    MAIN,

    /* 대주제에 속한 소주제다. */
    SUB
}
