package com.module06.backend.capture.infrastructure.ai;

import java.util.List;
import java.util.Objects;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.module06.backend.capture.application.port.out.TupleVectorIndexPort;
import com.module06.backend.capture.infrastructure.ai.dto.VectorIndexDtos.VectorUpsertItemDto;
import com.module06.backend.capture.infrastructure.ai.dto.VectorIndexDtos.VectorUpsertRequestDto;
import com.module06.backend.capture.infrastructure.ai.dto.VectorIndexDtos.VectorUpsertResponseDto;

/*
 * AI-08(`POST /internal/vector/upsert`) 호출 어댑터다.
 *
 * <h2>계층 어댑터와 나눈 이유</h2>
 * {@link AiLayerHttpAdapter} 는 실패를 {@code AiLayerException} 으로 올린다 — 그 예외는
 * analysis_layer 에 error_code 로 저장되고 CAP-06 으로 화면까지 나가는 값이다. 벡터 반영은
 * **분석과 무관한 배경 작업**이라 그 경로에 실리면 안 된다. 화면에는 "분석이 실패했다"로
 * 보이는데 실제로는 예시 색인이 밀린 것뿐인 상태가 만들어진다.
 *
 * RestClient 빈은 같은 것을 쓴다. 토큰 헤더와 타임아웃이 한 곳에서 정해져야 하고, 여기서
 * 따로 만들면 그 설정이 두 벌이 된다.
 *
 * <h2>재시도하지 않는다</h2>
 * 재시도 단위는 **워커의 다음 주기**다(TupleVectorSyncService). 여기서 또 돌리면 한 주기에
 * 같은 배치를 여러 번 태우고, 임베딩 비용이 그만큼 곱해진다.
 */
@Slf4j
@Component
public class TupleVectorIndexHttpAdapter implements TupleVectorIndexPort {

    private static final String PATH = "/internal/vector/upsert";

    private final RestClient restClient;

    /*
     * 웹 계층이 쓰는 것과 같은 매퍼를 주입받는다(AiLayerHttpAdapter 와 같은 이유). 여기서 새로
     * 만들면 프로젝트의 직렬화 설정과 갈려서 이 경로만 다른 규칙으로 읽힌다.
     */
    private final ObjectMapper objectMapper;

    public TupleVectorIndexHttpAdapter(RestClient aiRestClient, ObjectMapper objectMapper) {
        this.restClient = aiRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<IndexedVector> upsert(List<VectorToIndex> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return List.of();
        }

        List<VectorUpsertItemDto> items = vectors.stream()
                .map(this::toItem)
                .filter(Objects::nonNull)
                .toList();
        if (items.isEmpty()) {
            return List.of();
        }

        VectorUpsertResponseDto response = restClient.post()
                .uri(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VectorUpsertRequestDto(items))
                .retrieve()
                .body(VectorUpsertResponseDto.class);

        if (response == null || response.upserted() == null) {
            /*
             * 2xx 인데 본문이 없다. **성공으로 세지 않는다** — 올라갔다고 표시해 두면 그 예시는
             * 다시 올릴 기회를 영영 잃고, 검색에는 안 걸리는데 원본은 "반영됨"으로 남는다.
             * 아무것도 안 올라간 것으로 두면 다음 주기가 다시 시도한다.
             */
            log.warn("벡터 색인 응답이 비어 있다 — 이번 배치는 반영하지 않는다. 요청={}건", vectors.size());
            return List.of();
        }

        return response.upserted().stream()
                .map(result -> new IndexedVector(result.vectorId(), result.pointId()))
                .toList();
    }

    /*
     * payload 를 객체로 바꿔 싣는다. Python 은 `payload: dict` 로 받으므로 문자열 그대로
     * 보내면 422 다.
     *
     * 파싱이 실패하면 **그 행만 빼고 보낸다.** 깨진 payload 는 다시 보내도 계속 깨져 있어서,
     * 배치 전체를 세우면 그 한 행이 뒤의 정상 행을 영원히 막는다(워커의 시도 횟수 상한이
     * 같은 이유로 있다). 로그로 남겨 원본을 고칠 수 있게 한다.
     */
    private VectorUpsertItemDto toItem(VectorToIndex vector) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(vector.payload());
        } catch (JacksonException e) {
            log.warn("벡터 payload 를 해석할 수 없어 이 예시는 보내지 않는다. vectorId={}",
                    vector.vectorId(), e);
            return null;
        }

        return new VectorUpsertItemDto(
                vector.vectorId(),
                vector.companyId(),
                vector.layer().wireValue(),
                vector.inputText(),
                payload,
                vector.deptId(),
                vector.provenance().name());
    }
}
