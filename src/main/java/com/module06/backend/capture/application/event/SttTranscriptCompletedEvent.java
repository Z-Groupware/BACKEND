package com.module06.backend.capture.application.event;

/* 해당 회의의 STT 블록이 하나 이상 존재하고 모두 성공적으로 끝났다는 내부 신호다. */
public record SttTranscriptCompletedEvent(long meetingId) {
}
