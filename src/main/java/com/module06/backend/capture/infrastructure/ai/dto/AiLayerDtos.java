package com.module06.backend.capture.infrastructure.ai.dto;

import java.util.List;

/*
 * Spring ↔ Python 내부 API 의 전송 모양이다.
 *
 * **API 표면은 camelCase 다.** 여기 필드명이 곧 계약이므로 도메인 모델과 이름이 달라도
 * 명세를 따른다 — 도메인이 편한 이름을 쓰려고 여기서 바꾸면 계약이 조용히 갈린다.
 * 매핑은 어댑터가 흡수한다.
 *
 * 응답 DTO 는 Python 이 돌려주는 필드를 **전부 적지 않는다.** usedFewShot 처럼 지금 쓰지 않는
 * 값은 생략하고, Jackson 의 미지 속성 무시로 넘긴다. 다 적으면 Python 이 필드를 하나 늘릴
 * 때마다 이쪽이 깨진다.
 */
public final class AiLayerDtos {

    private AiLayerDtos() {
    }

    /* 계층에 넘기는 발화. transcript_chunk.offset_ms → startOffsetMs 매핑이 여기서 끝난다. */
    public record UtteranceDto(Long utteranceId, Long speakerId, Integer startMs, String text) {
    }

    /* 닫힌 목록. personId=null 이 unknown_person 탈출구다. */
    public record ParticipantDto(Long personId, String name) {
    }

    /* 계층별 토큰. 선택 항목이 아니다 — 없으면 QLTY-03(비용)이 성립하지 않는다. */
    public record UsageDto(Integer tokensIn, Integer tokensOut) {

        public int in() {
            return tokensIn != null ? tokensIn : 0;
        }

        public int out() {
            return tokensOut != null ? tokensOut : 0;
        }
    }

    // ── AI-03 · L2 주제 분할 ────────────────────────────────────────────────────

    public record SegmentTopicsRequestDto(
            Long tenantId,
            Long meetingId,
            List<UtteranceDto> utterances,
            String queryText
    ) {
    }

    public record TopicSegmentDto(
            Integer topicSeq,
            String topic,
            Long startUtteranceId,
            Long endUtteranceId,
            List<Long> utteranceIds
    ) {
    }

    public record SegmentTopicsResponseDto(
            List<TopicSegmentDto> topics,
            UsageDto usage,
            String model,
            String promptVersion
    ) {
    }

    // ── AI-04 · L3 주제별 정리 ──────────────────────────────────────────────────

    public record SummarizeTopicRequestDto(
            Long tenantId,
            Long meetingId,
            Integer topicSeq,
            String topic,
            List<UtteranceDto> utterances,
            List<ParticipantDto> participants,
            String queryText
    ) {
    }

    public record TopicItemDto(
            String itemType,
            String content,
            String reason,
            Long evidenceUtteranceId
    ) {
    }

    public record SummarizeTopicResponseDto(
            Integer topicSeq,
            String topic,
            String summary,
            List<TopicItemDto> items,
            UsageDto usage,
            String model,
            String promptVersion
    ) {
    }

    // ── 오류 응답 ───────────────────────────────────────────────────────────────

    /*
     * Python 이 실패에 실어 주는 분류다.
     *
     * `retryable` 을 응답 본문에서 읽는 것이 핵심이다 — 메시지 문자열로 재시도를 추측하면
     * 영구 실패를 세 번 재시도해 토큰만 태운다. 판정은 실패를 만든 쪽이 한다.
     */
    public record LayerErrorDto(String code, String kind, Boolean retryable,
                               Double retryAfterSec, String message) {

        /*
         * 값이 없으면 재시도하지 않는다.
         *
         * 없다는 건 Python 의 LayerError 핸들러를 지나지 않은 응답(프록시 502, ALB 오류 등)이라는
         * 뜻이다. 그런 응답을 재시도 가능으로 낙관하면 게이트웨이가 죽어 있는 동안 계층을 계속
         * 때리게 된다. HTTP 상태로 판단하는 건 호출자 몫이고, 여기서는 "본문이 그렇게 말했는가"만 본다.
         */
        public boolean retryableOrFalse() {
            return Boolean.TRUE.equals(retryable);
        }

        public String codeOrUnknown() {
            return code != null && !code.isBlank() ? code : "AI_LAYER_ERROR";
        }
    }
}
