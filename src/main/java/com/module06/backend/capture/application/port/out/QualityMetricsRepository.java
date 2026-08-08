package com.module06.backend.capture.application.port.out;

/*
 * QLTY-02 품질 지표의 **원재료**를 읽는다. 비율 계산은 여기서 하지 않는다.
 *
 * <h2>왜 개수만 주고 비율은 서비스가 내나</h2>
 * "무엇을 TP 로 세는가"가 이 지표의 전부다 — MODIFY 를 성공으로 볼지, 사람이 직접 추가한
 * 액션을 FN 으로 볼지가 숫자를 통째로 바꾼다. 그 판단을 SQL 안에 넣으면 나중에 지표가 이상할 때
 * **왜 그렇게 세는지가 쿼리 문자열 안에 숨는다.** 개수는 DB 가 세고, 뜻은 코드가 갖는다.
 *
 * <h2>표본은 gold set 이 정한다</h2>
 * 정답 채점에 필요한 값은 review_log 에 이미 다 있다. gold set 의 역할은 **어느 회의로 잴지를
 * 고정하는 것**이다 — 그래야 프롬프트를 바꾼 뒤 같은 표본으로 다시 재서 비교할 수 있다.
 * 표본이 매번 달라지면 지표가 올라도 모델이 좋아진 것인지 쉬운 회의가 섞인 것인지 모른다.
 */
public interface QualityMetricsRepository {

    /* 회사의 gold set 표본 위에서 센 개수들. 표본이 비어 있으면 전부 0 이다. */
    MetricsTally tally(long companyId);

    /*
     * 채점에 쓰는 개수. **판정은 액션마다 마지막 것만 센다** — review_log 는 이력이라 사람이 두
     * 번 판정할 수 있고(반려했다가 다시 확인), 전부 세면 한 액션이 여러 번 채점된다.
     *
     * @param goldSetMeetingCount 표본 회의 수
     * @param reviewedActionCount 표본 안에서 사람이 판정한 액션 수(= 동결된 정답 수)
     * @param aiValidCount        AI 가 만든 액션 중 사람이 **액션으로 인정**한 수(CONFIRM·MODIFY).
     *                            MODIFY 를 여기 넣는 이유 — 담당자를 고쳤어도 "그 일이 있다"는
     *                            판정은 맞았다. 필드 정확도는 다른 축이다
     * @param aiRejectedCount     AI 가 만들었는데 사람이 아니라고 한 수(REJECT). 그게 FP 다
     * @param manualAddedCount    사람이 직접 추가한 액션 수(RVW-03). **AI 가 놓친 것**이라 FN 이다
     * @param autoConfirmedCount  게이트가 자동 확정한 tuple 수
     * @param autoConfirmedWrong  그중 사람이 고치거나 반려한 수. 게이트가 틀린 것이다
     * @param tupleCount          표본 안의 전체 tuple 수. needsReviewRate 의 분모다
     * @param model               채점 대상이 어느 모델의 출력인가. 없으면 null
     * @param promptVersion       같은 이유. 버전이 섞이면 지표를 비교할 수 없다
     */
    record MetricsTally(
            int goldSetMeetingCount,
            int reviewedActionCount,
            int aiValidCount,
            int aiRejectedCount,
            int manualAddedCount,
            int autoConfirmedCount,
            int autoConfirmedWrong,
            int tupleCount,
            String model,
            String promptVersion
    ) {
    }
}
