package com.module06.backend.capture.presentation.api.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

import com.module06.backend.capture.application.usecase.EditSummaryUseCase.EditSummaryCommand;
import com.module06.backend.capture.application.usecase.EditSummaryUseCase.ItemEditCommand;

/* ANLZ-04 요청. 항목 여러 개를 한 번에 고친다 — 화면이 요약 전체를 편집한 뒤 한 번 저장한다. */
public record EditSummaryRequest(

        @Schema(description = "고칠 항목 목록")
        @NotEmpty(message = "수정할 항목이 필요합니다.")
        @Valid
        List<ItemEdit> items
) {

    public EditSummaryCommand toCommand(long companyId, long meetingId, long editorMemberId) {
        return new EditSummaryCommand(companyId, meetingId, editorMemberId,
                items.stream()
                        .map(item -> new ItemEditCommand(item.itemId(), item.content(), item.reason()))
                        .toList());
    }

    public record ItemEdit(

            @Schema(description = "meeting_decision.id", example = "41")
            @NotNull(message = "항목 id 는 필수입니다.")
            Long itemId,

            @Schema(description = "고친 문장", example = "온보딩 플로우를 2단계로 축소")
            String content,

            /*
             * 선택이다. 안 보내면 AI 가 적은 분류 근거를 그대로 둔다 — 안 보낸 것을 "지워라"로
             * 읽으면 사람이 내용만 고쳤는데 근거가 조용히 사라진다.
             */
            @Schema(description = "분류 근거. 생략하면 기존 값을 유지한다", example = "사용자 테스트 결과 반영")
            String reason
    ) {
    }
}
