package com.module06.backend.meeting.presentation.api.response;

import java.util.List;

import com.module06.backend.meeting.application.result.CaptureSessionStartResult;

/*
 * CAP-01 캡처 세션 시작 성공 응답이다.
 */
public record CaptureSessionStartResponse(
        Long captureSessionId,
        String status,
        boolean isPaused,
        Long startedBy,
        long startedAtEpochMs,
        List<RosterEntryResponse> roster
) {

    /* 애플리케이션 결과를 명세의 문자열 상태와 roster 응답으로 변환한다. */
    public static CaptureSessionStartResponse from(CaptureSessionStartResult result) {
        /* 내부 enum을 공개 문자열로 바꾸고 닫힌 roster를 응답 전용 값으로 복사한다. */
        return new CaptureSessionStartResponse(
                result.captureSessionId(),
                result.status().name(),
                result.isPaused(),
                result.startedBy(),
                result.startedAtEpochMs(),
                result.roster().stream()
                        .map(rosterEntry -> new RosterEntryResponse(
                                rosterEntry.personKey(),
                                rosterEntry.memberId(),
                                rosterEntry.name(),
                                rosterEntry.type().name()
                        ))
                        .toList()
        );
    }

    /* 실제 구성원과 명단 외 sentinel을 동일한 JSON 구조로 반환한다. */
    public record RosterEntryResponse(
            String personKey,
            Long memberId,
            String name,
            String type
    ) {
    }
}
