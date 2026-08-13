package com.module06.backend.meeting.domain.model;

/*
 * MEET-02 회의 화면의 두 탭이 쓰는 조회 범위다.
 *
 * 지정하면 역할 기반 열람 범위(companyWideRead)와 무관하게 요청자 본인 기준으로만
 * 좁힌다 — OWNER·ADMIN이라도 "내가 개설한"·"참여해야 할" 탭에는 자기 회의만 보여야 한다.
 */
public enum MeetingListScope {

    /* 요청자가 host인 회의만 포함한다. */
    HOSTED,

    /* 요청자가 참석자이면서 host는 아닌 회의만 포함한다. */
    ATTENDING
}
