package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.module06.backend.capture.application.port.out.AnalysisArtifactRepository;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.ResolvedReference;
import com.module06.backend.capture.domain.model.TopicSegment;
import com.module06.backend.capture.infrastructure.persistence.entity.AnalysisLayerArtifactJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataAnalysisLayerArtifactRepository;

/*
 * 계층 산출물을 JSON 으로 접었다 편다(V5.20).
 *
 * <h2>직렬화가 여기 있는 이유</h2>
 * 포트는 {@code List<TopicSegment>} 로 말한다. JSON 은 저장 방식이지 계약이 아니다 —
 * 나중에 컬럼으로 펴거나 다른 저장소로 옮겨도 오케스트레이터는 그대로여야 한다.
 *
 * <h2>읽기 실패를 삼키지 않는다</h2>
 * 형태가 깨진 payload 를 빈 목록으로 돌려주면 재개가 <b>문맥 없이</b> 모델을 부른다.
 * 빈 문맥으로 부른 결과는 빈 결과이고, 그게 DONE 으로 기록되면 조회는 "분석 완료"라고 말한다.
 * 되살릴 수 없으면 되살릴 수 없다고 드러나야 한다.
 *
 * 반대로 <b>행이 없는 것</b>은 정상이다 — V5.20 이전에 분석된 회의가 그렇다. 그건 빈 목록이고,
 * 호출자가 보고 "되살릴 문맥이 없다"고 판정한다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AnalysisArtifactPersistenceAdapter implements AnalysisArtifactRepository {

    private final SpringDataAnalysisLayerArtifactRepository repository;

    /*
     * 앱의 공용 매퍼를 받는다. 이 payload 는 우리가 쓰고 우리가 읽는 값이고, 커서(ANLZ-05)처럼
     * 밖으로 나가 되돌아오는 값이 아니라 설정 변화에 민감하지 않다.
     */
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveReferences(long meetingId, List<ResolvedReference> references) {
        save(meetingId, LayerName.L1_5, references == null ? List.of() : references);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResolvedReference> findReferences(long meetingId) {
        return read(meetingId, LayerName.L1_5, new TypeReference<List<ResolvedReference>>() {
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveTopics(long meetingId, List<TopicSegment> topics) {
        save(meetingId, LayerName.L2, topics == null ? List.of() : topics);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicSegment> findTopics(long meetingId) {
        return read(meetingId, LayerName.L2, new TypeReference<List<TopicSegment>>() {
        });
    }

    /*
     * 있으면 갈아끼우고 없으면 만든다. 지우고 넣지 않는 이유 — UNIQUE(meeting_id, layer) 위라
     * 지운 뒤 넣기 사이에 다른 실행이 들어오면 충돌한다. 한 행을 고치는 편이 그 창을 없앤다.
     */
    private void save(long meetingId, LayerName layer, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        repository.findByMeetingIdAndLayer(meetingId, layer.wireValue())
                .ifPresentOrElse(
                        entity -> entity.replacePayload(json),
                        () -> repository.save(AnalysisLayerArtifactJpaEntity.of(meetingId, layer, json)));
    }

    private <T> List<T> read(long meetingId, LayerName layer, TypeReference<List<T>> type) {
        return repository.findByMeetingIdAndLayer(meetingId, layer.wireValue())
                .map(entity -> parse(meetingId, layer, entity.getPayload(), type))
                .orElseGet(List::of);
    }

    private <T> List<T> parse(long meetingId, LayerName layer, String payload, TypeReference<List<T>> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException e) {
            log.error("계층 산출물을 읽을 수 없다 — meetingId={} layer={}", meetingId, layer.wireValue(), e);
            throw new IllegalStateException(
                    "계층 산출물의 형태가 올바르지 않습니다: " + layer.wireValue(), e);
        }
    }
}
