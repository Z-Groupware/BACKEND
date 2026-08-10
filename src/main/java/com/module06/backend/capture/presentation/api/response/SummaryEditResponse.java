package com.module06.backend.capture.presentation.api.response;

import java.time.LocalDateTime;

import com.module06.backend.capture.application.usecase.EditSummaryUseCase.SummaryEdited;

/*
 * ANLZ-04 응답이다.
 *
 * <h2>labelLogged 를 내려준다</h2>
 * 명세가 요구하는 필드이고, 뜻은 "이 수정이 라벨로도 남았는가"다. 요약 수정의 본체는 문장이
 * 바뀌는 것이 아니라 {AI 가 낸 문장 → 사람이 인정한 문장} 한 쌍이 쌓이는 것이라, 그게 조용히
 * 실패하면 아무도 모른 채 개선 재료만 사라진다.
 *
 * editedCount 는 명세에 없지만 함께 준다 — 여러 항목을 한 번에 보내는 API 라 몇 건이 반영됐는지
 * 응답에 없으면 화면이 자기가 보낸 수를 믿는 수밖에 없다.
 */
public record SummaryEditResponse(
        LocalDateTime editedAt,
        boolean labelLogged,
        int editedCount
) {

    public static SummaryEditResponse from(SummaryEdited edited) {
        return new SummaryEditResponse(edited.editedAt(), edited.labelLogged(), edited.editedCount());
    }
}
