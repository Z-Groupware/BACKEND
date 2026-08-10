package com.module06.backend.capture.application.usecase;

import java.util.List;

import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.domain.model.LayerName;

/* ANLZ-02 · 요약 재시도(계층 재개). */
public interface ResumeAnalysisUseCase {

    /*
     * @param resumeFromLayer 전송 값("L4" · "L1.5"). enum 이름이 아니라 계층의 전송 값이다 —
     *                        DB·Python 계약과 같은 문자열을 쓴다({@link LayerName#wireValue()}).
     *                        <p>
     *                        <b>null 이면 이쪽이 고른다</b> — 파이프라인 순서로 처음 깨진 계층
     *                        (FAILED 또는 중단·#177)에서 재개한다. 화면의 「다시 분석」 버튼처럼
     *                        계층을 모르는 호출자를 위한 것이다. 깨진 계층이 없으면
     *                        RESUME_NOTHING_TO_RESUME(409) — 그 회의는 재개가 아니라 ANLZ-01 이
     *                        필요하다.
     *                        <p>
     *                        빈 문자열은 null 과 다르게 취급한다 — 계층을 보내려다 실패한 것이라
     *                        RESUME_LAYER_UNKNOWN(400) 이다. 자동 선택을 원하면 필드를 아예 뺀다.
     */
    ResumeOutcome resume(long companyId, long meetingId, String resumeFromLayer);

    /*
     * 재개 결과.
     *
     * {@code reusedLayers} 를 함께 주는 이유 — "무엇을 다시 안 태웠는지"가 이 API 의 값이다.
     * 재개했는데 앞 계층까지 다시 돌았다면 사용자는 응답만 보고는 알 수 없고, 알게 되는 것은
     * 청구서에서다.
     */
    record ResumeOutcome(AnalysisOutcome outcome, LayerName resumeFrom, List<LayerName> reusedLayers) {
    }
}
