package com.module06.backend.capture.infrastructure.ai.dto;

import java.util.List;

import tools.jackson.databind.JsonNode;

/*
 * AI-08(`/internal/vector/upsert`) 의 전송 모양이다.
 *
 * **API 표면은 camelCase 다.** Python 쪽 `app/schemas/vector.py` 의 CamelModel 이 그렇게
 * 노출하고, 그 모델은 `extra="forbid"` 라 **모르는 필드를 422 로 거절한다** — 여기서 이름을
 * 하나 바꾸면 그 자리에서 깨진다.
 *
 * ⚠ payload 는 **JsonNode 다. String 이 아니다.** DB(meeting_tuple_vector.payload)에는 JSON
 * 문자열로 들어 있지만 Python 쪽은 `payload: dict` 로 받으므로, 문자열로 그대로 실으면
 * 422 로 거절된다. 어댑터가 한 번 파싱해서 객체로 싣는다.
 *
 * 파싱만 하고 **내용은 해석하지 않는다.** 확정 tuple 의 모양을 아는 것은 만든 쪽(RVW-02)과
 * 쓰는 쪽(계층 프롬프트)이고, 옮기기만 하는 이 경로가 그 구조를 알면 tuple 이 바뀔 때마다
 * 여기도 바뀐다.
 */
public final class VectorIndexDtos {

    private VectorIndexDtos() {
    }

    /*
     * @param vectorId 원본 행 id(meeting_tuple_vector.id). **AI-08 이 이 값으로 포인트 id 를
     *                 결정적으로 만든다** — 그래서 재시도가 복제본을 만들지 않는다
     */
    public record VectorUpsertItemDto(
            Long vectorId,
            Long companyId,
            String layer,
            String inputText,
            JsonNode payload,
            Long deptId,
            String provenance
    ) {
    }

    public record VectorUpsertRequestDto(List<VectorUpsertItemDto> items) {
    }

    /* 행 단위 결과. 요청보다 적을 수 있고, 그 차이가 곧 "다시 보내야 할 행"이다. */
    public record VectorUpsertResultDto(Long vectorId, String pointId) {
    }

    public record VectorUpsertResponseDto(List<VectorUpsertResultDto> upserted, String model) {
    }
}
