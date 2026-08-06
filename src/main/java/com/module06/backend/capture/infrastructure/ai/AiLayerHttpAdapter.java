package com.module06.backend.capture.infrastructure.ai;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.ExtractTuplesResult;
import com.module06.backend.capture.application.port.out.GateResult;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.application.port.out.ResolveReferenceResult;
import com.module06.backend.capture.application.port.out.SegmentTopicsResult;
import com.module06.backend.capture.application.port.out.SummarizeTopicResult;
import com.module06.backend.capture.application.service.AiLayerException;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.GateStatus;
import com.module06.backend.capture.domain.model.GateVerdict;
import com.module06.backend.capture.domain.model.ItemType;
import com.module06.backend.capture.domain.model.ReferenceType;
import com.module06.backend.capture.domain.model.ResolvedReference;
import com.module06.backend.capture.domain.model.TopicItem;
import com.module06.backend.capture.domain.model.TopicSegment;
import com.module06.backend.capture.domain.model.Utterance;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.AssignmentTupleDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.ConfirmedItemDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.ExtractTuplesRequestDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.ExtractTuplesResponseDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.GateCandidateDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.GateRequestDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.GateResponseDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.GateVerdictDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.LayerErrorDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.ParticipantDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.ResolveReferenceRequestDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.ResolveReferenceResponseDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.ResolvedReferenceDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.SegmentTopicsRequestDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.SegmentTopicsResponseDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.SummarizeTopicRequestDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.SummarizeTopicResponseDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.TopicItemDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.TopicSegmentDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.UsageDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.UtteranceDto;

/*
 * AI EC2 내부 API 호출 어댑터다. Spring ↔ Python 계약이 실체로 만나는 유일한 자리다.
 *
 * <h2>재시도를 여기서 하지 않는다</h2>
 * Python 이 이미 계층 안에서 백오프 3회(2s·8s·30s ±20%)를 돌린다. 여기서 또 돌리면
 * 최악의 경우 9번 호출되고 토큰이 9배가 된다. 이쪽의 재시도 단위는 **계층**이고
 * (명세 「레이어 호출 실패 정책」), 그건 ANLZ-02 가 사람의 지시로 수행한다.
 *
 * <h2>실패를 분류해서 올린다</h2>
 * `retryable` 은 응답 본문에서 읽는다. 메시지 문자열로 추측하면 영구 실패를 재시도해
 * 토큰만 태운다 — 판정은 실패를 만든 쪽이 한다.
 *
 * <h2>오류에 응답 본문을 그대로 싣지 않는다</h2>
 * 계층 오류 메시지는 analysis_layer.error_message 에 저장되고 CAP-06 으로 화면까지 나간다.
 * 제공자 응답 본문이 거기 섞이면 되돌릴 수 없다(BACKEND PR #63 에서 같은 지적을 받은 자리).
 * 그래서 code·message 만 옮기고 길이도 자른다.
 */
@Slf4j
@Component
public class AiLayerHttpAdapter implements AiLayerPort {

    /* analysis_layer.error_message 가 VARCHAR(500) 이다. 넘치면 저장 단계에서 터진다. */
    private static final int ERROR_MESSAGE_MAX = 400;

    private final RestClient restClient;

    /*
     * 웹 계층이 쓰는 것과 같은 매퍼를 주입받는다. 여기서 새로 만들면 프로젝트의 직렬화 설정
     * (Spring Boot 4.1 은 Jackson 3 = tools.jackson 이다)과 갈려서, 오류 본문만 다른 규칙으로
     * 읽히는 상태가 된다.
     */
    private final ObjectMapper objectMapper;

    public AiLayerHttpAdapter(RestClient aiRestClient, ObjectMapper objectMapper) {
        this.restClient = aiRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResolveReferenceResult resolveReference(long tenantId, long meetingId, List<Utterance> utterances,
                                                  List<Long> targetUtteranceIds, List<Participant> participants) {
        ResolveReferenceResponseDto response = post(
                "/internal/layers/l1-5/resolve-reference",
                new ResolveReferenceRequestDto(tenantId, meetingId, toUtteranceDtos(utterances),
                        // null 이 아니라 빈 리스트를 보낸다 — pydantic 은 list 자리의 None 을 422 로 거절한다.
                        targetUtteranceIds != null ? targetUtteranceIds : List.of(),
                        toParticipantDtos(participants), null),
                ResolveReferenceResponseDto.class);

        return new ResolveReferenceResult(
                toResolvedReferences(response.references()),
                toLayerRun(response.usage(), response.model(), response.promptVersion()));
    }

    @Override
    public SegmentTopicsResult segmentTopics(long tenantId, long meetingId, List<Utterance> utterances) {
        SegmentTopicsResponseDto response = post(
                "/internal/layers/l2/segment-topics",
                new SegmentTopicsRequestDto(tenantId, meetingId, toUtteranceDtos(utterances), null),
                SegmentTopicsResponseDto.class);

        return new SegmentTopicsResult(
                toTopicSegments(response.topics()),
                toLayerRun(response.usage(), response.model(), response.promptVersion()));
    }

    @Override
    public SummarizeTopicResult summarizeTopic(long tenantId, long meetingId, int topicSeq, String topic,
                                               List<Utterance> utterances, List<Participant> participants) {
        SummarizeTopicResponseDto response = post(
                "/internal/layers/l3/summarize-topic",
                new SummarizeTopicRequestDto(tenantId, meetingId, topicSeq, topic,
                        toUtteranceDtos(utterances), toParticipantDtos(participants), null),
                SummarizeTopicResponseDto.class);

        return new SummarizeTopicResult(
                response.summary(),
                toTopicItems(response.items()),
                toLayerRun(response.usage(), response.model(), response.promptVersion()));
    }

    @Override
    public GateResult gate(long tenantId, long meetingId, String topic, List<GateCandidate> candidates,
                           List<Utterance> utterances, List<Participant> participants) {
        GateResponseDto response = post(
                "/internal/layers/l3-5/gate",
                new GateRequestDto(tenantId, meetingId, topic, toGateCandidateDtos(candidates),
                        toUtteranceDtos(utterances), toParticipantDtos(participants), null),
                GateResponseDto.class);

        return new GateResult(
                toGateVerdicts(response.verdicts()),
                toLayerRun(response.usage(), response.model(), response.promptVersion()));
    }

    @Override
    public ExtractTuplesResult extractTuples(long tenantId, long meetingId, String topic,
                                             List<ConfirmedItem> items, List<Utterance> utterances,
                                             List<Participant> participants, LocalDate meetingDate) {
        ExtractTuplesResponseDto response = post(
                "/internal/layers/l4/extract-tuples",
                new ExtractTuplesRequestDto(tenantId, meetingId, topic, toConfirmedItemDtos(items),
                        toUtteranceDtos(utterances), toParticipantDtos(participants), null,
                        // ISO 문자열로 고정한다. LocalDate 를 그대로 넘기면 Jackson 설정에 따라
                        // 배열로 직렬화될 여지가 있고, Python 의 date 는 그걸 422 로 거절한다.
                        meetingDate != null ? meetingDate.toString() : null,
                        "EXTRACT"),
                ExtractTuplesResponseDto.class);

        return new ExtractTuplesResult(
                toAssignmentTuples(response.tuples()),
                toLayerRun(response.usage(), response.model(), response.promptVersion()));
    }

    /*
     * 공통 호출. 오류를 AiLayerException 으로 통일해 오케스트레이터가 HTTP 라이브러리에
     * 묶이지 않게 한다.
     */
    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            T response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    // onStatus 는 ResponseSpec 의 메서드다 — retrieve() 없이 부르면 컴파일되지 않는다.
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        throw toException(path, httpResponse);
                    })
                    .body(responseType);

            if (response == null) {
                // 2xx 인데 본문이 없다. 재시도해도 같으므로 영구 실패다.
                throw new AiLayerException("EMPTY_RESPONSE",
                        path + " 응답 본문이 비어 있습니다.", false);
            }
            return response;
        } catch (AiLayerException e) {
            throw e;
        } catch (HttpMessageConversionException | RestClientResponseException e) {
            // 본문을 읽었는데 우리 DTO 로 안 바뀐다 = Python 과 우리 계약이 어긋났다.
            // 재시도하면 같은 응답을 또 파싱하려 하므로 낫지 않고, 계층당 3회면 토큰이 3배다.
            // (같은 이유로 review-loop 의 ProviderAvailability 도 직렬화 실패를 재시도에서 뺐다.)
            throw new AiLayerException("AI_LAYER_CONTRACT_MISMATCH",
                    clip(path + " 응답을 해석할 수 없습니다: " + e.getClass().getSimpleName()), false, e);
        } catch (RuntimeException e) {
            // 연결 실패·타임아웃. 제공자·네트워크 문제이므로 재시도 가능으로 분류한다.
            // 계층은 3회 후 FAILED 로 멈추므로 무한히 도는 경로는 아니다.
            throw new AiLayerException("AI_LAYER_UNREACHABLE",
                    clip(path + " 호출 실패: " + e.getClass().getSimpleName()), true, e);
        }
    }

    /*
     * 오류 응답을 분류로 바꾼다.
     *
     * 본문이 Python 의 LayerError 모양이면 그 분류를 그대로 쓴다. 아니면(프록시 502, ALB 오류 등)
     * 상태코드로 판단한다 — 5xx 는 재시도 가능, 4xx 는 우리 요청 문제라 재시도해도 같다.
     */
    private AiLayerException toException(String path, ClientHttpResponse response) {
        int status;
        try {
            status = response.getStatusCode().value();
        } catch (IOException e) {
            return new AiLayerException("AI_LAYER_UNREACHABLE", clip(path + " 상태코드를 읽을 수 없습니다."), true, e);
        }

        LayerErrorDto error = readError(response);
        if (error != null && error.code() != null) {
            return new AiLayerException(error.codeOrUnknown(),
                    clip(path + " → " + status + " " + error.codeOrUnknown()),
                    error.retryableOrFalse());
        }

        boolean retryable = status >= 500;
        return new AiLayerException(retryable ? "AI_LAYER_UNAVAILABLE" : "AI_LAYER_REJECTED",
                clip(path + " → HTTP " + status), retryable);
    }

    private LayerErrorDto readError(ClientHttpResponse response) {
        try {
            return objectMapper.readValue(response.getBody().readAllBytes(), LayerErrorDto.class);
        } catch (Exception e) {
            // 본문이 JSON 이 아니거나 모양이 다르다. 상태코드로만 판단하도록 null 을 돌려준다.
            log.debug("계층 오류 본문을 해석하지 못했다 — 상태코드로 판단한다", e);
            return null;
        }
    }

    private List<UtteranceDto> toUtteranceDtos(List<Utterance> utterances) {
        return utterances.stream()
                .map(u -> new UtteranceDto(u.utteranceId(), u.speakerMemberId(), u.startOffsetMs(), u.text()))
                .toList();
    }

    private List<ParticipantDto> toParticipantDtos(List<Participant> participants) {
        return participants.stream()
                .map(p -> new ParticipantDto(p.personId(), p.name()))
                .toList();
    }

    private List<GateCandidateDto> toGateCandidateDtos(List<GateCandidate> candidates) {
        return candidates.stream()
                .map(c -> new GateCandidateDto(
                        // itemKey 는 문자열 계약이다. decision id 를 그대로 문자열화해 되짚는다.
                        String.valueOf(c.decisionId()),
                        c.itemType() != null ? c.itemType().name() : null,
                        c.content(),
                        c.evidenceUtteranceId()))
                .toList();
    }

    private List<ConfirmedItemDto> toConfirmedItemDtos(List<ConfirmedItem> items) {
        return items.stream()
                .map(item -> new ConfirmedItemDto(
                        item.itemType() != null ? item.itemType().name() : null,
                        // 값을 그대로 실어 보낸다. CONFIRMED 가 아니면 Python 이 422 로 거절하고,
                        // 그것이 이 필드의 목적이다 — 여기서 CONFIRMED 로 바꿔 채우면 게이트가 뚫린다.
                        item.gateStatus() != null ? item.gateStatus().name() : null,
                        item.content(),
                        item.evidenceUtteranceIds() != null ? item.evidenceUtteranceIds() : List.of()))
                .toList();
    }

    /*
     * 지시어 해소 결과를 도메인으로 옮긴다.
     *
     * 알 수 없는 referenceType 은 **UNRESOLVED 로 내린다.** ItemType 을 DISCUSSION 으로
     * 내리는 것과 같은 성질이지만 방향이 반대다 — 항목은 버리면 오간 내용이 사라지므로 살리고,
     * 지시어는 종류를 모르면 쓸 수 없으므로 기권으로 내린다. PERSON 인지 모르는 채 담당자
     * 판정에 쓰면 사람이 아닌 것이 담당자 후보가 된다.
     */
    private List<ResolvedReference> toResolvedReferences(List<ResolvedReferenceDto> references) {
        if (references == null) {
            return List.of();
        }
        return references.stream()
                .filter(Objects::nonNull)
                .map(r -> new ResolvedReference(
                        r.utteranceId(),
                        r.surface(),
                        parseReferenceType(r.referenceType()),
                        r.resolvedPersonId(),
                        r.resolvedText(),
                        r.evidenceUtteranceId()))
                .toList();
    }

    private ReferenceType parseReferenceType(String value) {
        try {
            return ReferenceType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("알 수 없는 referenceType 을 UNRESOLVED 로 내린다: {}", value);
            return ReferenceType.UNRESOLVED;
        }
    }

    /*
     * 게이트 판정을 도메인으로 옮긴다.
     *
     * itemKey 가 숫자가 아니거나 gateStatus 를 못 읽으면 **그 판정을 버린다.** 버린 항목은
     * gate_status 가 NULL 로 남아 L4 로 넘어가지 않는다 — 판정을 못 읽은 항목을 CONFIRMED 로
     * 낙관하면 게이트가 뚫리고, DISCUSSED 로 단정하면 확정된 일이 사라진다. 미판정이 정직하다.
     */
    private List<GateVerdict> toGateVerdicts(List<GateVerdictDto> verdicts) {
        if (verdicts == null) {
            return List.of();
        }
        return verdicts.stream()
                .filter(Objects::nonNull)
                .map(this::toGateVerdict)
                .filter(Objects::nonNull)
                .toList();
    }

    private GateVerdict toGateVerdict(GateVerdictDto dto) {
        Long decisionId = parseDecisionId(dto.itemKey());
        GateStatus status = GateStatus.fromNullable(dto.gateStatus());

        if (decisionId == null || status == null) {
            log.warn("게이트 판정을 되짚을 수 없어 버린다 — itemKey={} gateStatus={}",
                    dto.itemKey(), dto.gateStatus());
            return null;
        }
        return new GateVerdict(decisionId, status, dto.reason());
    }

    private Long parseDecisionId(String itemKey) {
        try {
            return Long.valueOf(itemKey);
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    /*
     * tuple 을 도메인으로 옮긴다.
     *
     * 근거 발화가 없는 tuple 은 **버린다.** 근거 없는 배정은 사람이 "정말 그런 말이 있었나"를
     * 확인할 수 없어 검토 자체가 불가능하고, 그 상태로 보드에 꽂히면 아무도 검증하지 못한 일이
     * 담당자에게 배정된다. Python 도 근거 없는 항목을 반환하지 않으므로 여기 걸리는 일은
     * 없어야 하지만, 계약이 갈렸을 때 조용히 통과하는 쪽으로 무너지지 않게 둔다.
     *
     * title 이 비어 있는 것도 같은 이유로 버린다 — "무엇을"이 없는 배정은 배정이 아니다.
     */
    private List<AssignmentTuple> toAssignmentTuples(List<AssignmentTupleDto> tuples) {
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
                .filter(Objects::nonNull)
                .filter(this::hasRequiredFields)
                .map(t -> new AssignmentTuple(
                        t.title(),
                        t.assigneeCandidatePersonId(),
                        AssigneeSource.fromNullable(t.assigneeSource()),
                        parseDueDate(t.dueDate()),
                        t.evidenceUtteranceId()))
                .toList();
    }

    private boolean hasRequiredFields(AssignmentTupleDto dto) {
        if (dto.evidenceUtteranceId() == null || dto.title() == null || dto.title().isBlank()) {
            log.warn("근거 또는 제목이 없는 tuple 을 버린다 — title={} evidenceUtteranceId={}",
                    dto.title(), dto.evidenceUtteranceId());
            return false;
        }
        return true;
    }

    /*
     * 마감일을 파싱한다. 형식이 어긋나면 null 로 두고 tuple 자체는 살린다 —
     * "누가·무엇을"은 유효하므로 버리면 손실이 더 크고, 기한은 사람이 검토할 때 채울 수 있다.
     * 반대로 그럴듯하게 틀린 날짜를 통과시키면 잘못된 마감이 보드에 꽂힌다.
     */
    private LocalDate parseDueDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            log.warn("dueDate 형식이 어긋나 기한 없이 저장한다: {}", value);
            return null;
        }
    }

    private List<TopicSegment> toTopicSegments(List<TopicSegmentDto> topics) {
        if (topics == null) {
            return List.of();
        }
        return topics.stream()
                .map(t -> new TopicSegment(
                        t.topicSeq() != null ? t.topicSeq() : 0,
                        t.topic(),
                        t.startUtteranceId(),
                        t.endUtteranceId(),
                        t.utteranceIds() != null ? t.utteranceIds() : List.of()))
                .toList();
    }

    /*
     * 알 수 없는 itemType 은 **버리지 않고 DISCUSSION 으로 내린다.**
     *
     * 버리면 오간 내용이 통째로 사라지지만, 내리면 최악이 '사람이 한 번 더 보는 것'이다.
     * Python 후처리도 같은 정책을 쓰므로 여기 걸리는 일은 없어야 하지만, 계약이 갈렸을 때
     * 조용히 유실되는 쪽으로 무너지지 않게 둔다.
     */
    private List<TopicItem> toTopicItems(List<TopicItemDto> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .map(item -> new TopicItem(
                        parseItemType(item.itemType()),
                        item.content(),
                        item.reason(),
                        item.evidenceUtteranceId()))
                .toList();
    }

    private ItemType parseItemType(String value) {
        try {
            return ItemType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("알 수 없는 itemType 을 DISCUSSION 으로 내린다: {}", value);
            return ItemType.DISCUSSION;
        }
    }

    private LayerRun toLayerRun(UsageDto usage, String model, String promptVersion) {
        // usage 가 없으면 실패로 둔다. 0 으로 채워 성공시키면 **비용 기준선이 조용히 틀어진다** —
        // 토큰을 쓴 호출이 0 으로 기록되고, QLTY-03 은 실제보다 싼 숫자를 보여주며,
        // 그 숫자로 특화 모델 전환의 손익분기점을 계산하게 된다. 경고 로그는 아무도 안 본다.
        //
        // 명세상 usage 는 선택 항목이 아니고 Python 의 LayerMeta 가 항상 채운다. 비어 있다면
        // 계약이 깨진 것이므로 재시도해도 같다 → 영구 실패.
        if (usage == null) {
            throw new AiLayerException("USAGE_MISSING",
                    "계층 응답에 usage 가 없습니다(model=" + model + "). 비용 집계가 불가능하므로 실패로 둡니다.",
                    false);
        }
        return new LayerRun(usage.in(), usage.out(), model, promptVersion);
    }

    private String clip(String message) {
        return message.length() <= ERROR_MESSAGE_MAX ? message : message.substring(0, ERROR_MESSAGE_MAX);
    }
}
