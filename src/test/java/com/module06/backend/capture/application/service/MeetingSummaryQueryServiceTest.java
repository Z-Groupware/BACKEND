package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.port.in.MeetingSummaryQueryPort.StalledMeetingSummary;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository.LayerState;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.application.port.out.MeetingAccessPort;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D(회의) 도메인이 묻는 요약 상태 배치 조회.
 *
 * <p>이 카드가 잘못 뜨는 대가가 비대칭이다 — 도는 중인 회의를 「중단됨」으로 보여주면 사람이
 * 멀쩡한 분석을 다시 눌러 토큰을 두 번 태운다. 반대로 늦게 뜨는 대가는 카드가 한 박자 늦는
 * 것뿐이다. 그래서 담지 않는 쪽을 검증한다.
 */
class MeetingSummaryQueryServiceTest {

    private static final long COMPANY = 7L;

    @Test
    @DisplayName("실패한 계층이 있으면 담는다 — isStalled=false(「실패했습니다」)")
    void 실패한_회의는_실패로_답한다() {
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(100L, done(LayerName.L1), state(LayerName.L1_5, LayerStatus.FAILED, false));

        List<StalledMeetingSummary> result = service(layers).findStalledSummaries(COMPANY, List.of(100L));

        assertThat(result).containsExactly(new StalledMeetingSummary(100L, false));
    }

    @Test
    @DisplayName("멈춘 RUNNING 만 있으면 중단이다 — isStalled=true(「중단됐습니다」)")
    void 중단된_회의는_중단으로_답한다() {
        // status 는 RUNNING 인데 심장이 멈췄다(#177). 배포·크래시로 끊긴 자리다.
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(101L, done(LayerName.L1), state(LayerName.L2, LayerStatus.RUNNING, true));

        List<StalledMeetingSummary> result = service(layers).findStalledSummaries(COMPANY, List.of(101L));

        assertThat(result).containsExactly(new StalledMeetingSummary(101L, true));
    }

    @Test
    @DisplayName("실패와 중단이 함께면 실패가 이긴다 — 다시 눌러도 안 풀릴 수 있는 쪽을 알려준다")
    void 둘_다면_실패가_이긴다() {
        /*
         * 「중단됐습니다」로 안내하면 사용자는 다시 누르면 된다고 읽는다. 그런데 실패한 계층이
         * 남아 있으면 같은 자리에서 또 멈추고, 그때 사용자는 시스템을 못 믿게 된다.
         */
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(102L,
                state(LayerName.L1_5, LayerStatus.FAILED, false),
                state(LayerName.L2, LayerStatus.RUNNING, true));

        List<StalledMeetingSummary> result = service(layers).findStalledSummaries(COMPANY, List.of(102L));

        assertThat(result).containsExactly(new StalledMeetingSummary(102L, false));
    }

    @Test
    @DisplayName("정상 요약된 회의는 담지 않는다")
    void 정상_회의는_담지_않는다() {
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(103L, done(LayerName.L1), done(LayerName.L1_5), done(LayerName.DIST));

        assertThat(service(layers).findStalledSummaries(COMPANY, List.of(103L))).isEmpty();
    }

    @Test
    @DisplayName("⚠ 아직 도는 중인 회의는 담지 않는다 — 멀쩡한 분석을 다시 누르게 만들면 안 된다")
    void 도는_중인_회의는_담지_않는다() {
        // 살아 있는 RUNNING 이다(stalled=false).
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(104L, done(LayerName.L1), state(LayerName.L2, LayerStatus.RUNNING, false));

        assertThat(service(layers).findStalledSummaries(COMPANY, List.of(104L))).isEmpty();
    }

    @Test
    @DisplayName("분석 이력이 없는 회의는 담지 않는다 — 「중단」이 아니라 아직 안 한 것이다")
    void 분석_이력이_없으면_담지_않는다() {
        // 계층 행이 아예 없으면 배치 조회의 키로도 나오지 않는다.
        assertThat(service(new FakeLayerStates()).findStalledSummaries(COMPANY, List.of(105L))).isEmpty();
    }

    @Test
    @DisplayName("다른 회사 회의는 던지지 않고 뺀다 — 카드 하나 때문에 화면 전체가 죽으면 안 된다")
    void 남의_회사_회의는_조용히_뺀다() {
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(200L, state(LayerName.L2, LayerStatus.FAILED, false));  // 내 회사
        layers.put(999L, state(LayerName.L2, LayerStatus.FAILED, false));  // 남의 회사

        // 200 만 이 회사 것이다.
        MeetingAccessPort access = (companyId, meetingId) -> companyId == COMPANY && meetingId == 200L;

        List<StalledMeetingSummary> result =
                new MeetingSummaryQueryService(access, layers).findStalledSummaries(COMPANY, List.of(200L, 999L));

        assertThat(result).containsExactly(new StalledMeetingSummary(200L, false));
    }

    @Test
    @DisplayName("계층 상태를 회의별로 한 번에 읽는다 — 회의 수만큼 쿼리가 나가면 N+1 이다")
    void 배치로_한_번에_읽는다() {
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(300L, state(LayerName.L2, LayerStatus.FAILED, false));
        layers.put(301L, state(LayerName.L4, LayerStatus.RUNNING, true));
        layers.put(302L, done(LayerName.DIST));

        List<StalledMeetingSummary> result =
                service(layers).findStalledSummaries(COMPANY, List.of(300L, 301L, 302L));

        assertThat(layers.batchCalls).isEqualTo(1);
        assertThat(result).containsExactlyInAnyOrder(
                new StalledMeetingSummary(300L, false),
                new StalledMeetingSummary(301L, true));
    }

    @Test
    @DisplayName("입력이 비면 조회 자체를 하지 않는다")
    void 빈_입력은_조회하지_않는다() {
        FakeLayerStates layers = new FakeLayerStates();

        assertThat(service(layers).findStalledSummaries(COMPANY, List.of())).isEmpty();
        assertThat(service(layers).findStalledSummaries(COMPANY, null)).isEmpty();
        assertThat(service(layers).findStalledSummaries(null, List.of(1L))).isEmpty();

        assertThat(layers.batchCalls).isZero();
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    /* 회사 관문은 통과시키고 계층 상태만 보는 조립. */
    private static MeetingSummaryQueryService service(FakeLayerStates layers) {
        return new MeetingSummaryQueryService((companyId, meetingId) -> true, layers);
    }

    private static LayerState done(LayerName layer) {
        return state(layer, LayerStatus.DONE, false);
    }

    private static LayerState state(LayerName layer, LayerStatus status, boolean stalled) {
        return new LayerState(layer, status, 0, 0, stalled);
    }

    /*
     * 계층 상태만 답하는 가짜다. 배치 호출 횟수를 세는 이유 — 이 포트를 만든 목적이 N+1 을
     * 없애는 것이라, 회의마다 부르는 구현으로 바뀌면 테스트가 알아야 한다.
     */
    private static final class FakeLayerStates implements AnalysisLayerRepository {

        private final Map<Long, List<LayerState>> byMeeting = new LinkedHashMap<>();
        private int batchCalls;

        private void put(long meetingId, LayerState... states) {
            byMeeting.put(meetingId, List.of(states));
        }

        @Override
        public Map<Long, List<LayerState>> findStatesByMeetings(List<Long> meetingIds) {
            batchCalls++;
            Map<Long, List<LayerState>> found = new LinkedHashMap<>();
            for (Long meetingId : new ArrayList<>(meetingIds)) {
                List<LayerState> states = byMeeting.get(meetingId);
                // 실물도 행이 없는 회의는 키로 만들지 않는다(계약).
                if (states != null) {
                    found.put(meetingId, states);
                }
            }
            return found;
        }

        @Override
        public List<LayerState> findStates(long meetingId) {
            throw new UnsupportedOperationException("이 서비스는 단건으로 읽지 않는다 — N+1 이 된다");
        }

        @Override
        public LockOutcome tryLock(long meetingId, LayerName layer, long runSeq) {
            throw new UnsupportedOperationException("조회 전용 서비스다");
        }

        @Override
        public void heartbeat(long meetingId, LayerName layer, int attempt) {
            throw new UnsupportedOperationException("조회 전용 서비스다");
        }

        @Override
        public void markDone(long meetingId, LayerName layer, int attempt, LayerRun run) {
            throw new UnsupportedOperationException("조회 전용 서비스다");
        }

        @Override
        public void markFailed(long meetingId, LayerName layer, int attempt, String errorCode,
                               String errorMessage, LayerRun run) {
            throw new UnsupportedOperationException("조회 전용 서비스다");
        }
    }
}
