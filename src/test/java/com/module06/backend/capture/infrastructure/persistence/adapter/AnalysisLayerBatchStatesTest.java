package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.module06.backend.capture.application.port.out.AnalysisLayerRepository.LayerState;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.infrastructure.persistence.entity.AnalysisLayerJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataAnalysisLayerRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 계층 상태 배치 조회의 IN 절 청킹.
 *
 * <p>DB 를 띄우지 않는다 — 검증 대상이 "몇 건씩 쪼개 보내는가"와 "쪼갠 결과를 어떻게 합치는가"라
 * 저장소 호출만 보면 그 두 가지가 그대로 드러난다. 실제 쿼리 동작은 컨테이너를 쓰는 다른
 * persistence 테스트들이 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalysisLayerBatchStatesTest {

    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-08-10T09:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private SpringDataAnalysisLayerRepository repository;

    @Captor
    private ArgumentCaptor<List<Long>> chunkCaptor;

    private AnalysisLayerPersistenceAdapter adapter() {
        // 잠금 획득자는 조회 경로에서 쓰이지 않는다.
        return new AnalysisLayerPersistenceAdapter(repository, null, FIXED);
    }

    @Test
    @DisplayName("200건을 넘으면 IN 절을 쪼개 보낸다 — 통째로 보내면 플레이스홀더가 그만큼 늘어난다")
    void 청크_경계를_넘으면_쪼개_보낸다() {
        // 450건 → 200 + 200 + 50
        List<Long> meetingIds = LongStream.rangeClosed(1, 450).boxed().toList();
        when(repository.findByMeetingIdInOrderByMeetingIdAscIdAsc(anyList())).thenReturn(List.of());

        adapter().findStatesByMeetings(meetingIds);

        verify(repository, org.mockito.Mockito.times(3))
                .findByMeetingIdInOrderByMeetingIdAscIdAsc(chunkCaptor.capture());
        assertThat(chunkCaptor.getAllValues()).extracting(List::size).containsExactly(200, 200, 50);
    }

    @Test
    @DisplayName("경계를 넘어도 회의별로 온전히 합쳐진다 — 청크가 갈라도 한 회의의 계층은 한 묶음이다")
    void 청크를_넘어도_회의별로_합쳐진다() {
        List<Long> meetingIds = LongStream.rangeClosed(1, 250).boxed().toList();

        // 첫 청크(1~200)에 회의 7, 두 번째 청크(201~250)에 회의 240.
        when(repository.findByMeetingIdInOrderByMeetingIdAscIdAsc(anyList()))
                .thenAnswer(invocation -> {
                    List<Long> chunk = invocation.getArgument(0);
                    if (chunk.contains(7L)) {
                        return List.of(done(7L, LayerName.L1), failed(7L, LayerName.L2));
                    }
                    if (chunk.contains(240L)) {
                        return List.of(done(240L, LayerName.L1));
                    }
                    return List.of();
                });

        Map<Long, List<LayerState>> states = adapter().findStatesByMeetings(meetingIds);

        assertThat(states.get(7L)).extracting(LayerState::layer)
                .containsExactly(LayerName.L1, LayerName.L2);
        assertThat(states.get(7L)).extracting(LayerState::status)
                .containsExactly(LayerStatus.DONE, LayerStatus.FAILED);
        assertThat(states.get(240L)).hasSize(1);
        // 계층 행이 없는 회의는 키로 나오지 않는다(포트 계약).
        assertThat(states).hasSize(2);
    }

    @Test
    @DisplayName("중복 id 는 접어서 보낸다 — 남겨두면 IN 절만 길어지고 결과는 같다")
    void 중복_id는_접는다() {
        when(repository.findByMeetingIdInOrderByMeetingIdAscIdAsc(anyList())).thenReturn(List.of());

        adapter().findStatesByMeetings(List.of(5L, 5L, 5L, 6L));

        verify(repository).findByMeetingIdInOrderByMeetingIdAscIdAsc(chunkCaptor.capture());
        assertThat(chunkCaptor.getValue()).containsExactly(5L, 6L);
    }

    @Test
    @DisplayName("빈 입력이면 조회하지 않는다")
    void 빈_입력은_조회하지_않는다() {
        assertThat(adapter().findStatesByMeetings(List.of())).isEmpty();
        assertThat(adapter().findStatesByMeetings(null)).isEmpty();

        verify(repository, never()).findByMeetingIdInOrderByMeetingIdAscIdAsc(anyList());
    }

    private static AnalysisLayerJpaEntity done(long meetingId, LayerName layer) {
        AnalysisLayerJpaEntity entity = running(meetingId, layer);
        entity.markDone(LayerRun.empty(), LocalDateTime.now(FIXED));
        return entity;
    }

    private static AnalysisLayerJpaEntity failed(long meetingId, LayerName layer) {
        AnalysisLayerJpaEntity entity = running(meetingId, layer);
        entity.markFailed("TEST", "테스트", LayerRun.empty(), LocalDateTime.now(FIXED));
        return entity;
    }

    private static AnalysisLayerJpaEntity running(long meetingId, LayerName layer) {
        return AnalysisLayerJpaEntity.running(meetingId, layer, LocalDateTime.now(FIXED));
    }
}
