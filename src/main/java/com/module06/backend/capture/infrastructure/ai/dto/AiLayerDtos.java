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

    /*
     * 계층에 넘기는 발화. transcript_chunk.offset_ms → startMs 매핑이 여기서 끝난다.
     *
     * ⚠ 필드명이 startMs 인 것은 의도다. Python 의 app/schemas/common.py Utterance 가
     * start_ms 이고 CamelModel(alias_generator=to_camel) 이 "startMs" 로 노출한다.
     * 그리고 그 모델은 extra="forbid" 라 **모르는 필드를 422 로 거절한다** — startOffsetMs 로
     * 바꾸면 L2·L3 요청이 그 자리에서 깨진다. (DB 컬럼명 offset_ms 와도, API 표면의
     * startOffsetMs 와도 다른 세 번째 이름이라 헷갈리기 쉬워 여기 적어 둔다.)
     */
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

    // ── AI-02 · L1.5 지시어 해소 ────────────────────────────────────────────────

    /*
     * targetUtteranceIds 를 **빈 배열로라도 반드시 보낸다.** Python 쪽은 default_factory 가
     * 있어 생략해도 되지만, null 을 실으면 Jackson 이 `"targetUtteranceIds": null` 로 직렬화하고
     * pydantic 은 list 자리의 None 을 422 로 거절한다(default 는 "키가 없을 때"만 쓰인다).
     */
    public record ResolveReferenceRequestDto(
            Long tenantId,
            Long meetingId,
            List<UtteranceDto> utterances,
            List<Long> targetUtteranceIds,
            List<ParticipantDto> participants,
            String queryText
    ) {
    }

    public record ResolvedReferenceDto(
            Long utteranceId,
            String surface,
            String referenceType,
            Long resolvedPersonId,
            String resolvedText,
            Long evidenceUtteranceId
    ) {
    }

    public record ResolveReferenceResponseDto(
            List<ResolvedReferenceDto> references,
            UsageDto usage,
            String model,
            String promptVersion
    ) {
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

    // ── OVERVIEW · 회의 개요 ────────────────────────────────────────────────────
    // 명세 번호는 AI-11 이다(AI-08·09 는 few-shot 이 쓴다). Python 엔드포인트도 그 번호로 실재한다.
    //
    // 발화를 싣지 않는다. 개요는 확정된 결론을 줄이는 것이고, 전사를 다시 읽히면 주제 요약과
    // **다른 말을 하는 개요**가 나온다 — 사용자는 어느 쪽을 믿을지 알 수 없다. 입력을 좁히는 것이
    // 곧 모순을 막는 방법이다(AiLayerPort#summarizeMeeting 주석).

    public record MeetingTopicDigestDto(
            Integer topicSeq,
            String topic,
            List<DigestItemDto> items
    ) {
    }

    /* 확정 항목 하나. 근거 발화 id 를 넘기지 않는다 — 개요는 특정 발화를 가리키지 않는다. */
    public record DigestItemDto(
            String itemType,
            String content
    ) {
    }

    public record SummarizeMeetingRequestDto(
            Long tenantId,
            Long meetingId,
            List<MeetingTopicDigestDto> topics,
            List<ParticipantDto> participants,
            String queryText
    ) {
    }

    public record SummarizeMeetingResponseDto(
            String overview,
            UsageDto usage,
            String model,
            String promptVersion
    ) {
    }

    // ── AI-05 · L3.5 확정/논의 게이트 ───────────────────────────────────────────

    /*
     * itemKey 가 문자열인 것은 Python 계약이다(meeting_decision.id 를 문자열로 넣어도 되고
     * 임시 순번을 넣어도 된다). 우리는 항상 decision id 를 쓴다 — 응답을 그 행에 바로
     * 적용할 수 있고, 임시 순번을 쓰면 응답과 행을 다시 맞춰야 한다.
     */
    public record GateCandidateDto(
            String itemKey,
            String itemType,
            String content,
            Long evidenceUtteranceId
    ) {
    }

    public record GateRequestDto(
            Long tenantId,
            Long meetingId,
            String topic,
            List<GateCandidateDto> items,
            List<UtteranceDto> utterances,
            List<ParticipantDto> participants,
            String queryText
    ) {
    }

    public record GateVerdictDto(
            String itemKey,
            String gateStatus,
            String reason
    ) {
    }

    public record GateResponseDto(
            List<GateVerdictDto> verdicts,
            UsageDto usage,
            String model,
            String promptVersion
    ) {
    }

    // ── AI-06 · L4 assignment tuple 추출 ────────────────────────────────────────

    /*
     * gateStatus 는 Python 쪽에서 Literal["CONFIRMED"] 다. 다른 값이면 422 이고, 그것이 이
     * 필드의 목적이다 — 게이트를 지나지 않은 항목이 tuple 추출로 흘러가는 경로를 스키마로 막는다.
     *
     * evidenceUtteranceIds 는 null 로 보내지 않는다(위 targetUtteranceIds 와 같은 이유).
     */
    public record ConfirmedItemDto(
            String itemType,
            String gateStatus,
            String content,
            List<Long> evidenceUtteranceIds
    ) {
    }

    /*
     * meetingDate 는 "YYYY-MM-DD" 문자열로 보낸다. Python 이 date 로 받으므로 형식이 어긋나면
     * 422 다 — LocalDate 를 그대로 넘기면 Jackson 설정에 따라 배열([2026,8,6])로 직렬화될
     * 여지가 있어, 어댑터에서 ISO 문자열로 고정한다.
     *
     * view 를 항상 "EXTRACT" 로 보낸다. EXTRACT_NARROW 는 L5 관점 다변화의 한쪽이고,
     * 그건 L5 를 붙일 때 Python 이 내부에서 쓴다 — 여기서 미리 보내면 관점 하나만 쓴 결과를
     * 검증된 것처럼 저장하게 된다.
     */
    public record ExtractTuplesRequestDto(
            Long tenantId,
            Long meetingId,
            String topic,
            List<ConfirmedItemDto> items,
            List<UtteranceDto> utterances,
            List<ParticipantDto> participants,
            String queryText,
            String meetingDate,
            String view
    ) {
    }

    public record AssignmentTupleDto(
            String title,
            Long assigneeCandidatePersonId,
            String assigneeSource,
            String dueDate,
            Long evidenceUtteranceId
    ) {
    }

    public record ExtractTuplesResponseDto(
            List<AssignmentTupleDto> tuples,
            UsageDto usage,
            String model,
            String promptVersion
    ) {
    }

    // ── AI-07 · L5 관점 다변화 검증 ─────────────────────────────────────────────

    /*
     * tuple **하나**를 검증한다. 문맥을 통째로 다시 싣는 이유는 Python 이 내부에서 L4 를
     * 한 번 더(view=EXTRACT_NARROW) 돌리기 때문이다 — 그 재추출에 items·utterances·
     * participants·meetingDate 가 그대로 필요하다.
     *
     * ⚠ view 를 보내지 않는다. L4 요청과 달리 이 계약에는 그 필드가 없다(관점을 고르는 것은
     * Python 안의 일이다). CamelModel 이 extra="forbid" 라 모르는 필드를 실으면 422 다.
     *
     * items 는 L4 에 넘긴 것과 **같은 ConfirmedItemDto** 를 쓴다. Python 쪽도 같은 TopicItem
     * 모델이라 gateStatus 가 Literal["CONFIRMED"] 로 강제되고, 검증 재추출이 게이트를 지나지
     * 않은 항목을 보는 경로가 여기서도 막힌다.
     */
    public record VerifyRequestDto(
            Long tenantId,
            Long meetingId,
            String topic,
            AssignmentTupleDto tuple,
            List<ConfirmedItemDto> items,
            List<UtteranceDto> utterances,
            List<ParticipantDto> participants,
            String meetingDate,
            String queryText
    ) {
    }

    /*
     * 관점 하나의 결과. **관점마다 채워지는 필드가 다르다** —
     *   EXTRACT_NARROW  tuple (재현되지 않았으면 null)
     *   VERIFY          verdict · reason
     * error 는 그 관점이 실패했다는 뜻이고, 둘 다 실패하면 Python 이 계층 오류로 던진다.
     */
    public record ViewResultDto(
            String view,
            AssignmentTupleDto tuple,
            String verdict,
            String reason,
            String error
    ) {
    }

    /*
     * agree 는 두 관점이 같은 말을 했는가다. 필드가 갈린 목록이 disagreementFields 이고,
     * agree=true 면 비어 있다.
     *
     * results 는 관점별 원시 결과다. 우리가 저장하는 것은 VERIFY 쪽 verdict·reason 뿐이지만
     * 전부 받아 둔다 — 실패 원인이 '불일치'인지 '한쪽이 죽음'인지는 여기서만 구분된다.
     */
    public record VerifyResponseDto(
            Boolean agree,
            List<String> disagreementFields,
            List<ViewResultDto> results,
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
