package com.module06.backend.notification.application.usecase;

/*
 * 스케줄러가 애플리케이션 세부 구현을 알지 않고 회의 10분 전 알림 처리를 실행하는 유스케이스다.
 */
public interface SendMeetingRemindersUseCase {

    /* 현재 분을 기준으로 정확히 10분 뒤 시작하는 예약 회의 알림을 저장하고 발행한다. */
    void sendReminders();
}
