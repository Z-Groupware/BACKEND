package com.module06.backend.notification.infrastructure.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.module06.backend.notification.application.usecase.SendMeetingRemindersUseCase;

/*
 * 매분 정각에 회의 10분 전 알림 유스케이스를 실행하는 스케줄 진입점이다.
 */
@Component
@EnableScheduling
public class MeetingReminderScheduler {

    /* 예상하지 못한 실행 실패가 다음 스케줄까지 중단시키지 않도록 기록하는 로거다. */
    private static final Logger log = LoggerFactory.getLogger(MeetingReminderScheduler.class);

    /* 회의 대상 조회와 회원별 알림 처리를 담당하는 애플리케이션 유스케이스다. */
    private final SendMeetingRemindersUseCase sendMeetingRemindersUseCase;

    /* 스케줄러가 구현 서비스가 아닌 유스케이스 경계에만 의존하도록 주입한다. */
    public MeetingReminderScheduler(SendMeetingRemindersUseCase sendMeetingRemindersUseCase) {
        /* 주입받은 알림 유스케이스를 매분 실행에 사용한다. */
        this.sendMeetingRemindersUseCase = sendMeetingRemindersUseCase;
    }

    /* KST 기준 매분 0초에 실행해 10분 뒤 시작 시각의 한 분 시간창을 처리한다. */
    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void sendTenMinuteReminders() {
        try {
            /* 시간 계산과 중복 방지는 애플리케이션 서비스에 위임한다. */
            sendMeetingRemindersUseCase.sendReminders();
        } catch (RuntimeException exception) {
            /* 예상하지 못한 예외도 스케줄러 스레드 밖으로 전파하지 않고 다음 분 실행을 보장한다. */
            log.error("회의 10분 전 알림 스케줄 실행 실패", exception);
        }
    }
}
