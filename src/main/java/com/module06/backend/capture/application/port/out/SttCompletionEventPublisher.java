package com.module06.backend.capture.application.port.out;

import com.module06.backend.capture.application.event.SttTranscriptCompletedEvent;

/* STT 전체 성공 완료 신호를 비동기 분석 입구로 전달한다. */
public interface SttCompletionEventPublisher {

    /* 마지막 블록이 DONE 으로 닫힌 뒤에만 호출한다. */
    void publish(SttTranscriptCompletedEvent event);
}
