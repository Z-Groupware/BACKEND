package com.module06.backend.meeting.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * 회의 도메인의 현재 시각 판정에 사용하는 Clock을 제공한다.
 *
 * API 계약이 오프셋 없는 KST 로컬 시각이므로 시스템 기본 타임존과 무관하게 Asia/Seoul을 명시한다.
 */
@Configuration
public class MeetingTimeConfiguration {

    /* 전체 회의 서비스가 공유할 한국 표준시 식별자다. */
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    /* 운영에서는 실제 KST 시계를 제공하고 테스트에서는 고정 Clock으로 교체할 수 있게 한다. */
    @Bean
    public Clock meetingClock() {
        /* 시스템 UTC 순간을 KST 로컬 시각으로 해석하는 표준 Clock을 반환한다. */
        return Clock.system(KOREA_ZONE);
    }
}
