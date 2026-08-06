package com.module06.backend.meeting.application.result;

import java.util.List;

import com.module06.backend.meeting.domain.model.CaptureSessionStatus;

/*
 * CAP-01 성공 결과와 A·프론트가 사용할 고정 roster를 프레젠테이션 계층에 전달한다.
 */
public record CaptureSessionStartResult(
        Long captureSessionId,
        CaptureSessionStatus status,
        boolean isPaused,
        Long startedBy,
        long startedAtEpochMs,
        List<RosterEntry> roster
) {

    /* 외부에서 전달한 가변 목록이 성공 결과를 나중에 바꾸지 못하도록 복사한다. */
    public CaptureSessionStartResult {
        /* roster는 세션 시작 시점에 닫히므로 불변 목록으로 보관한다. */
        roster = List.copyOf(roster);
    }

    /* 예약 참석자와 명단 외 sentinel을 동일한 응답 구조로 표현한다. */
    public record RosterEntry(
            String personKey,
            Long memberId,
            String name,
            RosterType type
    ) {
    }

    /* roster 항목이 실제 구성원인지 명단 외 sentinel인지 구분한다. */
    public enum RosterType {

        /* 예약된 실제 구성원 항목이다. */
        MEMBER,

        /* 예약 명단에 없는 외부 발화자를 선택하기 위한 응답 전용 항목이다. */
        UNKNOWN
    }
}
