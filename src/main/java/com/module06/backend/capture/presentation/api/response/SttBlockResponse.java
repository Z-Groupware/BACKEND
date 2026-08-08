package com.module06.backend.capture.presentation.api.response;

import java.util.List;

import com.module06.backend.capture.application.port.out.SttBlockRepository.SttBlockView;

/*
 * STT-03 응답이다.
 *
 * <h2>audioS3Key 는 내려주지 않는다</h2>
 * 재제출에 필요한 내부 저장 위치이고 화면이 쓸 값이 아니다. 실어 보내면 버킷 구조가 응답에
 * 드러나고, 한 번 나간 계약은 되돌리기 어렵다.
 *
 * <h2>cutReason 은 내려준다</h2>
 * 화면 장식이 아니다. FALLBACK_OVERLAP 인 블록은 **경계에서 발화가 잘렸을 수 있어서**, 그
 * 구간의 요약이 이상할 때 사람이 원인을 짚을 수 있어야 한다(V5.4 주석).
 */
public record SttBlockResponse(List<BlockResponse> blocks) {

    public static SttBlockResponse from(List<SttBlockView> blocks) {
        return new SttBlockResponse(blocks.stream()
                .map(block -> new BlockResponse(
                        block.blockSeq(),
                        block.startOffsetMs(),
                        block.endOffsetMs(),
                        block.status().name(),
                        block.provider(),
                        block.cutReason().name(),
                        block.retryCount(),
                        // 실패 사유 코드다. 제공자 메시지가 아니라 우리가 분류한 값만 나간다.
                        block.error()))
                .toList());
    }

    /*
     * @param error 실패했을 때만 값이 있다(JOB_FAILED 등). 화면은 이 코드로 문구를 고른다 —
     *              제공자 메시지를 그대로 보여주면 되돌릴 수 없다
     */
    public record BlockResponse(
            int blockSeq,
            int startOffsetMs,
            int endOffsetMs,
            String status,
            String provider,
            String cutReason,
            int retryCount,
            String error
    ) {
    }
}
