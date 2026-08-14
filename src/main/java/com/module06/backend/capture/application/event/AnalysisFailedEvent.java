package com.module06.backend.capture.application.event;

/*
 * 회의 종료 후 자동으로 시작된 백그라운드 분석이 실패한 뒤 발행하는 내부 이벤트다.
 *
 * MeetingCompletedAnalysisTrigger가 runAnalysisUseCase.run() 결과를 확인한 뒤, 이미
 * 그 실행의 트랜잭션이 끝난 다음(비동기 스레드) 발행한다 — 그래서 알림 소비자는 D의
 * MeetingCanceledEvent 등과 달리 AFTER_COMMIT을 기다리지 않는다(대기할 트랜잭션 자체가 없다).
 */
public record AnalysisFailedEvent(
        Long companyId,
        Long meetingId,
        Long hostMemberId,
        String title,
        String errorCode
) {
}
