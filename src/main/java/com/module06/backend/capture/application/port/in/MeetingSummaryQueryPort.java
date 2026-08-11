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

    /*
     * 회의별 요약 상태 전체를 준다(MEET-04 회의 상세 「하달된 팀 액션」 영역).
     *
     * <h2>findStalledSummaries 와 다른 질문이다</h2>
     * 위쪽은 **깨진 회의만 걸러내는** 계약이다(마이페이지 카드) — 정상·진행 중·미시작을 담지
     * 않는다. 이쪽은 **모든 회의의 현재 상태**를 답한다. 하나로 합치지 않은 이유는 호출자가
     * 원하는 것이 다르기 때문이다: 카드는 "문제 있는 것만 모아 보여줘"이고, 상세 화면은
     * "이 회의가 지금 어느 단계냐"다. 합치면 카드가 DONE 항목까지 받아 걸러내야 한다.
     *
     * <h2>모든 회의에 대해 항목이 나간다</h2>
     * 계층 기록이 없는 회의도 {@code NONE}(또는 {@code WAITING_TRANSCRIPT})으로 채워 내린다.
     * 빠뜨리면 호출자가 "응답에 없으면 미시작"으로 추측하게 되고, 그 추측은 **회사 범위에서
     * 빠진 회의와 구분되지 않는다.**
     *
     * 회사 범위를 지나지 못한 회의는 항목이 아예 없다. 예외로 올리지 않는다 — 카드 하나
     * 때문에 화면 전체가 죽지 않게(#100 · filterInCompany 와 같은 규칙).
     *
     * @param companyId  토큰의 회사. null 이면 조회 없이 빈 목록
     * @param meetingIds 확인할 회의. null 이거나 비면 조회 없이 빈 목록
     * @return 입력 순서를 유지한다. 중복 id 는 접혀서 한 번만 나온다
     */
    List<MeetingSummaryStatus> findSummaryStatuses(Long companyId, List<Long> meetingIds);

    /* 회의 하나의 요약 상태. */
    record MeetingSummaryStatus(Long meetingId, SummaryStatus status) {
    }

    /*
     * 화면이 구분해야 하는 요약 상태.
     *
     * <h2>⚠ 이 값을 호출자가 다시 계산하면 안 된다</h2>
     * 계층 상태를 회의 하나로 접는 규칙(실패 우선 · 멈춘 RUNNING 은 실패로)은 ProcessingStatus
     * 에 있고 CAP-06 처리 상태 화면이 같은 값을 쓴다. 호출자가 계층 목록을 받아 따로 접으면
     * 마이페이지 카드 · 처리 상태 화면 · MEET-04 가 같은 회의를 서로 다르게 말한다.
     *
     * <h2>캐시하면 안 된다</h2>
     * {@link #STALLED} 는 **시각의 함수**다 — 심장이 멈춘 뒤 5분이 지나야 중단으로 판정된다
     * (LayerLiveness.STALE_AFTER). 같은 회의가 4분 전에는 PROCESSING 이었고 지금은 STALLED 다.
     */
    enum SummaryStatus {

        /*
         * 분석을 시작한 적이 없다 — 계층 기록이 하나도 없다.
         *
         * ⚠ 「실패」가 아니다. 지금은 자막 전송(CAP-11)이 붙기 전이라 발화가 0건이어서
         * 분석이 생략되는 회의가 여기 들어온다. 화면 문구를 「AI 요약 실패」로 쓰면
         * 정상적으로 아무 일도 일어나지 않은 회의를 사고로 보여주게 된다.
         *
         * 받아쓰기가 **전부 실패한** 회의도 여기다 — 기다릴 것이 없으므로 WAITING_TRANSCRIPT
         * 가 아니다. 그 실패는 STT-03(블록 상태)과 stt_gap 이 따로 보여준다.
         */
        NONE,

        /*
         * 받아쓰기가 아직 도는 중이라 분석을 시작하지 않았다.
         *
         * <h2>NONE 을 쪼갠 값이다 — 다른 상태를 가리지 않는다</h2>
         * 계층 기록이 **하나도 없을 때만** 이 값이 된다. 이미 한 번 분석된 회의(DONE)에 새
         * 녹음이 붙어 미완 블록이 생겨도 DONE 을 유지한다 — 그 회의에는 사람이 볼 요약이
         * 실제로 있고, 그걸 「받아쓰기 대기」로 덮으면 있는 요약이 화면에서 사라진다.
         *
         * 분석 시작 관문이 막고 있는 상태와 정확히 같은 조건이다(AnalysisOrchestrator 의
         * countUnfinished 검사). 관문은 막는데 화면이 「요약 없음」이라고 말하면, 기다리면
         * 되는 사용자가 뭔가 잘못됐다고 읽는다.
         */
        WAITING_TRANSCRIPT,

        /* 계층이 실제로 돌고 있다. 멈춘 RUNNING 은 여기가 아니라 STALLED 다. */
        PROCESSING,

        /*
         * 파이프라인이 끝까지 갔다.
         *
         * <h2>액션이 하달됐다는 뜻이다</h2>
         * 분배(DIST)가 파이프라인의 **마지막 계층**이고, 분배 0건이면 그 계층을 FAILED 로
         * 남기고 던진다(TupleDistributionService). 그래서 DONE 이면 액션이 최소 1건 있다 —
         * 「요약은 됐는데 액션이 0건」은 이 값으로 나오지 않는다.
         *
         * 다만 그 액션이 **검토·확정됐다는 뜻은 아니다.** 확정 여부는 action 도메인의
         * 미확정 건수로 판단한다(reviewStatus == PENDING).
         */
        DONE,

        /*
         * 중단 — 계층이 RUNNING 인데 그 실행이 죽었다(배포·크래시 · #177).
         * 다시 누르면 대개 그대로 이어진다.
         */
        STALLED,

        /*
         * 실패 — 계층이 실제로 실패했다. 같은 이유로 또 실패할 수 있다.
         *
         * 한 회의에 실패한 계층과 멈춘 계층이 함께 있으면 **이쪽이 이긴다.** 중단은 재시도로
         * 풀리지만 실패는 안 풀릴 수 있어, 사람이 알아야 할 정보량이 큰 쪽을 보여준다
         * (StalledMeetingSummary 가 고른 것과 같은 규칙).
         */
        FAILED
    }
}
