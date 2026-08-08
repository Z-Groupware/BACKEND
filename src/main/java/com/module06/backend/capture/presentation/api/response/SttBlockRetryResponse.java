package com.module06.backend.capture.presentation.api.response;

import com.module06.backend.capture.application.usecase.RetrySttBlockUseCase.RetryAccepted;

/*
 * STT-04 응답이다. **접수됐다는 뜻이지 끝났다는 뜻이 아니다**(202).
 *
 * retryCount 를 함께 준다 — 잡 이름에 들어간 값과 같고(meeting-500-block-3-r3), 화면이
 * "3번째 시도 중"을 보여줄 근거다. 몇 번이나 실패했는지가 곧 이 블록을 포기할지 판단하는
 * 재료라 숨기지 않는다.
 */
public record SttBlockRetryResponse(int blockSeq, String status, int retryCount) {

    public static SttBlockRetryResponse from(RetryAccepted accepted) {
        return new SttBlockRetryResponse(
                accepted.blockSeq(), accepted.status().name(), accepted.retryCount());
    }
}
