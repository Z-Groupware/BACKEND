package com.module06.backend.identity.member.application.usecase;

import java.time.LocalDate;

/**
 * 휴직 종료일이 지난 계정을 재직으로 되돌린다(WORKFLOW.md §7, 2026-08-16 확정).
 *
 * <p>화면이 없는 유스케이스다 — 사람이 승인 버튼을 누르지 않는다. 부르는 것은 스케줄러뿐이지만
 * 인터페이스로 두는 이유는 두 가지다. 스케줄러(infrastructure)가 서비스 구현을 직접 알면
 * ARCH_003(application ← infrastructure 단방향)이 깨지고, 날짜를 인자로 받는 형태라야
 * 테스트와 수동 실행이 같은 경로를 쓴다.
 */
public interface ReturnExpiredVacationsUseCase {

    /**
     * @param today 오늘 날짜(KST)
     * @return 재직으로 되돌린 인원 수
     */
    int returnExpired(LocalDate today);
}
