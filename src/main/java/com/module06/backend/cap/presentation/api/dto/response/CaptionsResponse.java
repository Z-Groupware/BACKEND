package com.module06.backend.cap.presentation.api.dto.response;

import com.module06.backend.cap.application.usecase.GetCaptionsUseCase;

import java.math.BigDecimal;
import java.util.List;

// 자막 전체 조회 응답 JSON — { "captions": [ { seq, personId, startMs, endMs, text, rms } ] }
public record CaptionsResponse(List<CaptionItem> captions) {

    public record CaptionItem(int seq, Long personId, int startMs, int endMs, String text, BigDecimal rms) {
    }

    // usecase 결과 → 응답 DTO
    public static CaptionsResponse from(GetCaptionsUseCase.Result result) {
        List<CaptionItem> items = result.captions().stream()
                .map(item -> new CaptionItem(item.seq(), item.personId(), item.startMs(), item.endMs(),
                        item.text(), item.rms()))
                .toList();
        return new CaptionsResponse(items);
    }
}
