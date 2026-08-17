package com.module06.backend.identity.member.infrastructure.scheduling;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.module06.backend.identity.member.application.usecase.ReturnExpiredVacationsUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 휴직 종료일이 지난 계정을 매일 재직으로 되돌린다(WORKFLOW.md §7, 2026-08-16 확정).
 *
 * <h2>왜 00:05 인가</h2>
 * 경계가 날짜 단위라("종료일 당일까지 휴직, 다음날부터 재직") 자정 직후에 한 번만 돌면 된다.
 * 5분을 띄우는 것은 자정 정각에 몰리는 다른 작업과 겹치지 않게 하기 위해서다. 그날 첫 출근보다는
 * 한참 앞이라 사용자가 "복직했는데 아직 휴직으로 보인다"를 겪지 않는다.
 *
 * <h2>@EnableScheduling 을 여기 붙이지 않는다</h2>
 * {@code TupleVectorSyncScheduler} 가 이미 붙였다. 켜는 자리가 여러 개가 되면 한쪽을 지울 때
 * 다른 워커까지 조용히 멈춘다({@code VocabularyLifecycleScheduler} 주석의 규약).
 *
 * <h2>⚠ 기본값이 꺼짐인 이유 — 첫 실행이 한꺼번에 복직시킨다</h2>
 * 이 배치는 "어제 끝난 휴직"이 아니라 {@code endDate < today} <b>전부</b>를 훑는다. 그래서 켜는
 * 순간 종료일이 몇 달 지난 사람까지 한 번에 재직으로 바뀐다. 그 범위를 팀이 아직 승인하지 않았다
 * (TODO.md 의 선행 팀결정 "휴직 자동 복귀의 기준 시각과 기존 데이터 백필을 정한다" 중 백필 쪽이
 * 미해결이다 — 기준 시각만 정해졌다). 켜기 전에 대상 인원을 세어 팀에 공유한다:
 *
 * <pre>
 * SELECT COUNT(*) FROM member m
 *   JOIN handover h ON h.writer_member_id = m.id
 *  WHERE m.status = 'VACATION' AND m.deleted_at IS NULL
 *    AND h.handover_type = 'VACATION' AND h.status = 'FINALIZED'
 *    AND DATE(h.leave_end_at) &lt; CURDATE();
 * </pre>
 *
 * <h2>⚠ 인스턴스를 늘리면 잠금이 필요하다</h2>
 * 지금은 단일 인스턴스이고 프로젝트에 ShedLock 의존성이 없다. 둘 이상이 동시에 돌면 양쪽이 같은
 * 행을 읽어 {@code returnFromVacation()} 을 부르고, 늦은 쪽은 이미 ACTIVE 라
 * {@code requireStatus(VACATION)} 이 AU-009 를 던져 <b>그 트랜잭션 전체가 롤백된다</b> —
 * 한 명 때문에 그 회차의 복직이 통째로 날아간다. 다음 주기에 다시 되므로 영구 손실은 아니지만,
 * 인스턴스를 늘릴 때는 ShedLock 이나 조건부 UPDATE 로 바꿔야 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "member.vacation-auto-return", name = "enabled", havingValue = "true")
public class VacationReturnScheduler {

    private final ReturnExpiredVacationsUseCase returnExpiredVacationsUseCase;

    /** 프로젝트 전체에 Clock 빈이 하나뿐이라(MeetingTimeConfiguration#meetingClock, KST) 타입으로 주입된다. */
    private final Clock clock;

    /* KST 매일 00:05. 6필드 표기는 MeetingReminderScheduler 와 같다. */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void returnExpiredVacations() {
        /*
         * 예외를 삼킨다. 스케줄러가 예외를 받으면 그 작업이 다음 주기부터 아예 안 돈다 —
         * 휴직자가 영원히 안 풀리고, 그 사람은 다음 휴직·퇴사 신청도 못 하는 상태로 굳는다.
         */
        try {
            returnExpiredVacationsUseCase.returnExpired(LocalDate.now(clock));
        } catch (RuntimeException e) {
            log.error("휴직 자동 복귀 배치 실패 — 다음 주기에 다시 시도한다", e);
        }
    }
}
