package com.module06.backend.cap.application.command;

// 녹음 종료(조립 트리거, CAP-05) 명령 — 컨트롤러가 path/토큰/본문을 합쳐 만든다.
// callerId는 헤더가 아니라 JWT principal에서 꺼낸 값이다(남의 id로 조립 트리거 차단).
public record StartRecordingAssemblyCommand(
        Long meetingId,
        Long callerId,
        int lastSegmentSeq,
        int lastSeq
) {
}
