package com.module06.backend.identity.member.application.port.out;

import java.time.LocalDate;
import java.util.List;

/**
 * 휴직 종료일이 지난 계정을 재직으로 되돌린다.
 *
 * <p>{@code handover.MemberStatusPort} 에 넣지 않는 이유: 저 포트는 <b>인수인계 도메인이 계정
 * 도메인에 요청하는</b> 창구다. 이 배치는 아무도 요청하지 않는다 — 날짜가 지났다는 사실만으로
 * 계정 도메인이 스스로 하는 일이라 이쪽 포트에 둔다.
 *
 * <p>종료일의 단일 출처는 {@code handover.leave_end_at} 이다. member 테이블로 복제하지 않는다 —
 * 두 벌이 되면 인수인계에서 날짜를 고쳤을 때 어느 쪽이 정본인지 알 수 없게 된다.
 */
public interface VacationReturnPort {

    /**
     * 종료일이 지난 휴직자를 재직으로 되돌리고, 되돌린 구성원의 id 를 돌려준다.
     *
     * <p>경계는 <b>종료일 다음날</b>이다(2026-08-16 팀 확정 — 8/20 종료면 8/20 까지 휴직,
     * 8/21 부터 재직). 즉 {@code endDate < today} 인 사람만 대상이다.
     *
     * @param today 오늘 날짜(KST). 배치가 {@code Clock} 으로 정해 넘긴다 — 구현이 직접
     *              {@code now()} 를 부르면 테스트가 날짜를 고정할 수 없다.
     * @return 재직으로 되돌린 memberId 목록. 0건이면 빈 리스트.
     */
    List<Long> returnExpiredVacations(LocalDate today);
}
