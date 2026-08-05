package com.module06.backend.capture.application.usecase;

import com.module06.backend.capture.application.result.AnalysisOutcome;

/*
 * ANLZ-01 · 요약 수동 실행이다.
 *
 * **기본 경로가 아니다.** 분석은 MEET-08(회의 종료)에서 자동으로 시작되고, 이 API 는 자동
 * 실행이 스킵된 회의를 수동으로 돌릴 때와 완료된 분석을 강제로 다시 돌릴 때만 쓴다(명세).
 */
public interface RunAnalysisUseCase {

    /*
     * @param force 이미 완료된 분석을 다시 돌린다. **재과금이 발생한다** — 그래서 기본값이 아니고,
     *              화면에서도 확인 모달로 그 사실을 노출한다.
     */
    AnalysisOutcome run(long companyId, long meetingId, boolean force);
}
