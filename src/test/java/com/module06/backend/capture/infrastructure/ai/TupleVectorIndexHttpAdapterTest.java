package com.module06.backend.capture.infrastructure.ai;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

import com.module06.backend.capture.application.port.out.TupleVectorIndexPort.IndexedVector;
import com.module06.backend.capture.application.port.out.TupleVectorIndexPort.VectorToIndex;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.VectorProvenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * AI-08 호출 어댑터.
 *
 * <p>검증의 축은 <b>못 보낼 행이 배치를 통째로 망가뜨리지 않는가</b>다. Python 은
 * {@code payload: dict} 를 요구하므로 객체가 아닌 payload 하나가 섞이면 요청 전체가 422 가 되고,
 * <b>같이 실려 간 정상 행까지 실패로 기록된다.</b> 그게 다섯 번 반복되면 정상 행이 시도 횟수
 * 상한에 걸려 조회에서 빠진다 — 상한은 깨진 행을 자르라고 둔 것인데 멀쩡한 행을 자르게 된다.
 */
class TupleVectorIndexHttpAdapterTest {

    @Test
    @DisplayName("객체가 아닌 payload 는 그 행만 빼고 보낸다 — 배치 전체가 422 가 되면 정상 행도 죽는다")
    void 객체가_아닌_payload는_빼고_보낸다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/internal/vector/upsert"))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    // 정상 행만 실려야 한다.
                    assertThat(body).contains("\"vectorId\":1");
                    assertThat(body).doesNotContain("\"vectorId\":2");
                    assertThat(body).doesNotContain("\"vectorId\":3");
                })
                .andRespond(withSuccess("""
                        {"upserted":[{"vectorId":1,"pointId":"point-1"}],"model":"gemini-embedding-001"}
                        """, MediaType.APPLICATION_JSON));

        TupleVectorIndexHttpAdapter adapter =
                new TupleVectorIndexHttpAdapter(builder.build(), new ObjectMapper());

        List<IndexedVector> indexed = adapter.upsert(List.of(
                vector(1L, "{\"title\":\"정리\"}"),
                // 배열은 파싱은 되지만 dict 가 아니다 — readTree 만으로는 안 걸린다.
                vector(2L, "[1,2,3]"),
                // 깨진 JSON. 이쪽은 파싱에서 걸린다.
                vector(3L, "{not json")));

        assertThat(indexed).extracting(IndexedVector::vectorId).containsExactly(1L);
        server.verify();
    }

    @Test
    @DisplayName("보낼 행이 하나도 없으면 아예 호출하지 않는다 — 빈 배치로 왕복할 이유가 없다")
    void 보낼_행이_없으면_호출하지_않는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 아무 기대도 걸지 않는다 — 호출이 일어나면 verify 에서 걸린다.

        TupleVectorIndexHttpAdapter adapter =
                new TupleVectorIndexHttpAdapter(builder.build(), new ObjectMapper());

        assertThat(adapter.upsert(List.of(vector(1L, "\"문자열\"")))).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("2xx 인데 본문이 비면 아무것도 반영하지 않는다 — 성공으로 세면 다시 올릴 기회를 잃는다")
    void 빈_응답은_성공으로_세지_않는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("/internal/vector/upsert"))
                .andRespond(withSuccess("{\"model\":\"gemini-embedding-001\"}", MediaType.APPLICATION_JSON));

        TupleVectorIndexHttpAdapter adapter =
                new TupleVectorIndexHttpAdapter(builder.build(), new ObjectMapper());

        assertThat(adapter.upsert(List.of(vector(1L, "{\"title\":\"정리\"}")))).isEmpty();
        // verify 가 없으면 요청을 아예 안 보내도 이 테스트가 통과한다 — 그러면 검증하려던
        // "2xx 인데 본문이 빈" 경로를 지나지 않는다(CodeRabbit PR #219 지적).
        server.verify();
    }

    private static VectorToIndex vector(long id, String payload) {
        return new VectorToIndex(id, 7L, LayerName.L4, "서준님이 정리해주세요.",
                payload, null, VectorProvenance.HUMAN_VERIFIED);
    }
}
