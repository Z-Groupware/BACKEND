package com.module06.backend.capture.application.usecase;

import java.time.LocalDateTime;
import java.util.List;

/* ANLZ-04 · 요약 수정. */
public interface EditSummaryUseCase {

    SummaryEdited edit(EditSummaryCommand command);

    record EditSummaryCommand(
            long companyId,
            long meetingId,
            long editorMemberId,
            List<ItemEditCommand> items
    ) {
    }

    /* reason 은 선택이다 — 안 보내면 AI 가 적은 분류 근거를 그대로 둔다. */
    record ItemEditCommand(long itemId, String content, String reason) {
    }

    /*
     * labelLogged 를 함께 준다(명세 응답).
     *
     * 화면이 쓰라고 주는 값이 아니라 **라벨이 실제로 남았는지**를 응답에서 확인할 수 있게 하는
     * 값이다. 요약 수정의 본체는 문장이 바뀌는 것이 아니라 {AI 가 낸 문장 → 사람이 인정한 문장}
     * 한 쌍이 쌓이는 것이고, 그게 조용히 실패하면 아무도 모른 채 재료만 사라진다.
     */
    record SummaryEdited(LocalDateTime editedAt, boolean labelLogged, int editedCount) {
    }
}
