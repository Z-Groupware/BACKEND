package com.module06.backend.capture.infrastructure.ai;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.application.port.out.SegmentTopicsResult;
import com.module06.backend.capture.application.port.out.SummarizeTopicResult;
import com.module06.backend.capture.application.service.AiLayerException;
import com.module06.backend.capture.domain.model.ItemType;
import com.module06.backend.capture.domain.model.TopicItem;
import com.module06.backend.capture.domain.model.TopicSegment;
import com.module06.backend.capture.domain.model.Utterance;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.LayerErrorDto;
import com.module06.backend.capture.infrastructure.ai.dto.AiLayerDtos.ParticipantDto;
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
        } catch (RuntimeException e) {
            // 연결 실패·타임아웃. 제공자·네트워크 문제이므로 재시도 가능으로 분류한다.
            // 본문 파싱 실패도 여기 걸리는데 그건 계약 불일치이고 재시도로 낫지 않는다 —
            // 다만 둘을 여기서 가르려면 예외 타입에 기대야 해서, 안전한 쪽(재시도 가능)으로 둔다.
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
        // usage 가 없으면 0 으로 둔다. 다만 그건 정상이 아니다 — 명세상 선택 항목이 아니고,
        // 0 이 쌓이면 QLTY-03 의 비용 기준선이 실제보다 싸게 보인다.
        if (usage == null) {
            log.warn("계층 응답에 usage 가 없다 — 비용 집계가 과소된다. model={}", model);
            return new LayerRun(0, 0, model, promptVersion);
        }
        return new LayerRun(usage.in(), usage.out(), model, promptVersion);
    }

    private String clip(String message) {
        return message.length() <= ERROR_MESSAGE_MAX ? message : message.substring(0, ERROR_MESSAGE_MAX);
    }
}
