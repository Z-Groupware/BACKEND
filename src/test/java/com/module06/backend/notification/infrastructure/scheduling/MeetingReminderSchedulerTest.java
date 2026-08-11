package com.module06.backend.notification.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/*
 * 회의 10분 전 스케줄의 실행 주기와 예외 격리 계약을 검증한다.
 */
@DisplayName("회의 10분 전 알림 스케줄러")
class MeetingReminderSchedulerTest {

    /* 스케줄 메서드가 KST 매분 정각 계약을 선언하는지 검증한다. */
    @Test
    @DisplayName("Asia/Seoul 기준 매분 0초에 실행한다")
    void runsEveryMinuteInKoreaTime() throws NoSuchMethodException {
        /* 실행 메서드의 Spring 스케줄 메타데이터를 조회한다. */
        Method method = MeetingReminderScheduler.class.getMethod("sendTenMinuteReminders");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        /* 크론과 타임존이 프론트 계약의 분 단위 알림 기준과 일치해야 한다. */
        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 * * * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    /* 정상 실행에서 알림 유스케이스를 정확히 한 번 호출하는지 검증한다. */
    @Test
    @DisplayName("한 번의 스케줄 실행에서 유스케이스를 한 번 호출한다")
    void delegatesOnce() {
        /* 유스케이스 호출 횟수를 기록하는 함수형 대역을 준비한다. */
        AtomicInteger invocationCount = new AtomicInteger();
        MeetingReminderScheduler scheduler = new MeetingReminderScheduler(invocationCount::incrementAndGet);

        /* 스케줄 메서드를 한 번 실행한다. */
        scheduler.sendTenMinuteReminders();

        /* 실제 시간 계산과 발행 로직은 유스케이스에 한 번만 위임돼야 한다. */
        assertThat(invocationCount).hasValue(1);
    }

    /* 예상하지 못한 유스케이스 예외도 다음 분 실행을 위해 격리하는지 검증한다. */
    @Test
    @DisplayName("유스케이스 실패를 스케줄러 밖으로 전파하지 않는다")
    void isolatesUseCaseFailure() {
        /* 호출 즉시 예외가 발생하는 유스케이스 대역을 주입한다. */
        MeetingReminderScheduler scheduler = new MeetingReminderScheduler(() -> {
            throw new RuntimeException("알림 처리 실패");
        });

        /* 스케줄러는 실패를 로그로 남기고 호출자에게 예외를 전파하지 않아야 한다. */
        assertThatCode(scheduler::sendTenMinuteReminders).doesNotThrowAnyException();
    }
}
