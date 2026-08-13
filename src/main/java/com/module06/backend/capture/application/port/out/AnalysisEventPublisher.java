package com.module06.backend.capture.application.port.out;

import com.module06.backend.capture.application.event.AnalysisCompletedEvent;
import com.module06.backend.capture.application.event.AnalysisFailedEvent;

/*
 * 분석 완료·실패 내부 이벤트를 외부 메시지 전달 방식과 분리하는 아웃바운드 포트다.
 */
public interface AnalysisEventPublisher {

    /* 분석 완료 이벤트를 애플리케이션 이벤트 채널에 발행한다. */
    void publish(AnalysisCompletedEvent event);

    /* 분석 실패 이벤트를 애플리케이션 이벤트 채널에 발행한다. */
    void publish(AnalysisFailedEvent event);
}
