package com.module06.backend.capture.application.usecase;

import com.module06.backend.capture.domain.model.SttBlockStatus;

/*
 * STT-04 · 특정 블록 재처리.
 *
 * **실패한 블록만 다시 돌린다.** 전체 재처리가 아니라 그 블록 하나여서 재과금이 최소화된다 —
 * 10분짜리 블록 하나가 실패했다고 회의 두 시간을 다시 돌리면 요금이 그만큼 다시 나간다.
 */
public interface RetrySttBlockUseCase {

    RetryAccepted retry(RetrySttBlockCommand command);

    /*
     * @param provider 생략하면 그 블록이 쓰던 제공자를 그대로 쓴다. 다른 값을 주면 **제공자를
     *                 바꿔 다시 돌리는 것**이고, 그게 이 API 를 둔 이유 중 하나다 —
     *                 같은 제공자로 세 번 실패한 블록은 네 번째도 대개 같은 결과다
     */
    record RetrySttBlockCommand(long companyId, long meetingId, int blockSeq, String provider) {
    }

    /* 응답에 실리는 값. 접수됐다는 뜻이지 끝났다는 뜻이 아니다(202). */
    record RetryAccepted(int blockSeq, SttBlockStatus status, int retryCount) {
    }
}
