package com.module06.backend.capture.application.port.in;

import java.util.List;

/*
 * capture(A, 이태연)가 선언하고 meeting(D, 모성진) 도메인이 호출하는 인바운드 포트다.
 *
 * D 는 analysis_layer·meeting_summary 엔티티를 직접 참조하지 않고 이 계약으로만 요약 상태를
 * 묻는다(MeetingActionQueryPort 와 동일 패턴 — action(C)이 선언하고 D 가 부르는 그 구조를
 * 방향만 바꿔 옮겼다).
 *
 * <h2>배치인 이유</h2>
 * 마이페이지 카드가 회의 목록을 한 번에 그린다. 회의마다 단건 조회를 부르면 회의 수만큼
 * 쿼리가 나가고(N+1), 카드 20개면 20번이다. 크기 제한은 두지 않는다 — 청킹은 구현체
 * 관심사이지 계약이 아니다(MeetingActionQueryPort 가 정한 규칙과 같다).
 *
 * <h2>회사 스코프는 이쪽이 다시 확인한다</h2>
 * D 가 자기 회의 목록을 보내지만 companyId 를 받아 **여기서 한 번 더 거른다.** 받아놓고 쓰지
 * 않아 다른 회사 데이터가 새어 나간 적이 실제로 있다(CAP-06 · 이슈 #100). 남의 회사 id 가
 * 섞여 오면 예외가 아니라 그 항목만 빠진다 — 카드 하나 때문에 화면 전체가 죽지 않게
 * (MeetingAccessPort#filterInCompany 주석).
 */
public interface MeetingSummaryQueryPort {

    /*
     * 요약이 **중단·실패한 회의만** 돌려준다(마이페이지 「요약이 중단된 회의」 카드).
     *
     * 정상 요약된 회의(DONE) · 아직 도는 중인 회의(RUNNING) · 한 번도 분석하지 않은 회의는
     * 결과에 담기지 않는다. 호출자가 방어적으로 걸러낼 필요가 없다.
     *
     * ⚠ **도는 중인 회의를 담지 않는 것이 이 계약의 핵심이다.** 아직 처리 중인 회의를
     * 「중단됨」으로 보여주면 사람이 멀쩡한 분석을 다시 눌러 토큰을 두 번 태운다.
     *
     * @param companyId  토큰의 회사. null 이면 조회 없이 빈 목록
     * @param meetingIds 확인할 회의. null 이거나 비면 조회 없이 빈 목록
     */
    List<StalledMeetingSummary> findStalledSummaries(Long companyId, List<Long> meetingIds);

    /*
     * 요약이 깨진 회의 하나.
     *
     * <h2>isStalled 가 화면 문구를 가른다</h2>
     * {@code true} 는 **중단**이다 — 계층이 RUNNING 인데 심장이 멈췄다(배포·크래시 · #177).
     * 다시 누르면 대개 그대로 이어진다.
     * {@code false} 는 **실패**다 — 계층이 실제로 실패했다. 같은 이유로 또 실패할 수 있다.
     *
     * <h2>둘 다면 실패가 이긴다</h2>
     * 한 회의에 실패한 계층과 멈춘 계층이 함께 있을 수 있다. 그때 {@code false}(실패)로
     * 답한다 — 중단은 재시도로 풀리지만 실패는 안 풀릴 수 있어서, 사람이 알아야 할 정보량이
     * 더 큰 쪽을 보여주는 것이 맞다. 화면 문구가 "중단"인데 다시 눌러도 같은 자리에서 또
     * 멈추면 사용자는 시스템을 못 믿게 된다.
     *
     * <h2>⚠ 이 값은 캐시하면 안 된다</h2>
     * {@code isStalled} 는 **시각의 함수**다. 심장이 멈춘 뒤 5분이 지나야 중단으로 판정되므로
     * (LayerLiveness.STALE_AFTER), 같은 회의가 4분 전에는 「처리 중」이라 결과에 없었고 지금은
     * 「중단」으로 나온다. 목록을 새로 그릴 때마다 다시 물어야 한다.
     */
    record StalledMeetingSummary(Long meetingId, boolean isStalled) {
    }
}
