package com.module06.backend.capture.application.usecase;

import java.util.List;

import com.module06.backend.capture.application.port.out.SttBlockRepository.SttBlockView;

/* STT-03 · 블록별 처리 상태 조회. 회의 종료 후 요약이 왜 늦는지·어디가 실패했는지를 본다. */
public interface GetSttBlocksUseCase {

    List<SttBlockView> getSttBlocks(long companyId, long meetingId);
}
