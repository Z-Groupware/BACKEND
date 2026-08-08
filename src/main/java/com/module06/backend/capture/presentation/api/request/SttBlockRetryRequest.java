package com.module06.backend.capture.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/*
 * STT-04 요청이다. 본문 전체가 선택이다 — `{"provider":"whisper"}` 또는 아예 없음.
 *
 * **생략하면 그 블록이 쓰던 제공자를 그대로 쓴다.** 여기서 기본값을 지어내면 whisper 로 돌던
 * 블록이 재처리 한 번에 조용히 aws-transcribe 로 바뀐다 — 사람은 "다시 돌린다"를 눌렀을 뿐인데
 * 제공자가 바뀌고, 그 뒤 정확도 차이의 원인을 찾을 수 없게 된다.
 */
public record SttBlockRetryRequest(
        @Schema(description = "다른 제공자로 다시 돌릴 때만 준다. 생략하면 그 블록이 쓰던 제공자를 유지한다",
                example = "whisper")
        // 컬럼이 VARCHAR(40) 이다. 넘치면 저장 단계에서 터져 재처리 자체가 500 이 된다.
        @Size(max = 40, message = "provider 는 40자를 넘을 수 없습니다.")
        String provider
) {
}
