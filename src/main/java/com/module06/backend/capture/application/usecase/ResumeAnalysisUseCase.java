package com.module06.backend.capture.application.usecase;

import java.util.List;

import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.domain.model.LayerName;

/* ANLZ-02 · 요약 재시도(계층 재개). */
public interface ResumeAnalysisUseCase {

    /*
     * @param resumeFromLayer 전송 값("L4" · "L1.5"). enum 이름이 아니라 계층의 전송 값이다 —
     *                        DB·Python 계약과 같은 문자열을 쓴다({@link LayerName#wireValue()})
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
