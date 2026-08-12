package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.port.in.MeetingSummaryQueryPort.MeetingSummaryStatus;
import com.module06.backend.capture.application.port.in.MeetingSummaryQueryPort.StalledMeetingSummary;
import com.module06.backend.capture.application.port.in.MeetingSummaryQueryPort.SummaryStatus;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository.LayerState;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.application.port.out.MeetingAccessPort;
import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @DisplayName("정상 요약된 회의는 담지 않는다 — 열 계층 전부 DONE 이어야 정상이다")
    void 정상_회의는_담지_않는다() {
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(103L, fullPipelineDone());

        assertThat(service(layers).findStalledSummaries(COMPANY, List.of(103L))).isEmpty();
    }

    @Test
    @DisplayName("⚠ 계층 일부만 남은 회의는 카드에도 오른다 — 상세 화면과 같은 판정이어야 한다")
    void 부분_완료_회의도_카드에_오른다() {
        /*
         * 예전 실행이 markDone(L4) 을 커밋한 뒤 tryLock(L5) 전에 죽은 모양이다. RUNNING 행이
         * 없어 #177 의 멈춤 판정에도 안 걸리고, 실패한 계층도 없다.
         *
         * 이 회의는 DIST 가 돌지 않았으므로 하달된 액션이 0건이다. 카드에 안 뜨면 아무도
         * 모른 채 남는다 — 예전 구현이 정확히 그랬다(CodeRabbit PR #365 지적).
         */
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(106L, done(LayerName.L1), done(LayerName.L1_5), done(LayerName.L2),
                done(LayerName.L3), done(LayerName.L3_5), done(LayerName.L4));

        assertThat(service(layers).findStalledSummaries(COMPANY, List.of(106L)))
                // 실패한 계층이 없으니 「중단」이다.
                .containsExactly(new StalledMeetingSummary(106L, true));
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

        List<StalledMeetingSummary> result = new MeetingSummaryQueryService(access, layers, blocksWaitingFor())
                .findStalledSummaries(COMPANY, List.of(200L, 999L));

        assertThat(result).containsExactly(new StalledMeetingSummary(200L, false));
    }

    @Test
    @DisplayName("계층 상태를 회의별로 한 번에 읽는다 — 회의 수만큼 쿼리가 나가면 N+1 이다")
    void 배치로_한_번에_읽는다() {
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(300L, state(LayerName.L2, LayerStatus.FAILED, false));
        layers.put(301L, state(LayerName.L4, LayerStatus.RUNNING, true));
        layers.put(302L, fullPipelineDone());

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

    // ── MEET-04 · 회의별 전체 상태 ───────────────────────────────────────────────

    @Test
    @DisplayName("다섯 계층 상태를 화면 값으로 접는다")
    void 계층_상태를_화면_값으로_접는다() {
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(400L, fullPipelineDone());                                             // 끝까지 갔다
        layers.put(401L, done(LayerName.L1), state(LayerName.L2, LayerStatus.RUNNING, false));
        layers.put(402L, done(LayerName.L1), state(LayerName.L2, LayerStatus.RUNNING, true));
        layers.put(403L, state(LayerName.L2, LayerStatus.FAILED, false));

        List<MeetingSummaryStatus> result = service(layers)
                .findSummaryStatuses(COMPANY, List.of(400L, 401L, 402L, 403L));

        assertThat(result).containsExactly(
                new MeetingSummaryStatus(400L, SummaryStatus.DONE),
                new MeetingSummaryStatus(401L, SummaryStatus.PROCESSING),
                new MeetingSummaryStatus(402L, SummaryStatus.STALLED),
                new MeetingSummaryStatus(403L, SummaryStatus.FAILED));
    }

    @Test
    @DisplayName("실패와 중단이 함께면 실패가 이긴다 — 카드 쪽과 같은 규칙이어야 한다")
    void 전체_조회에서도_실패가_이긴다() {
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(410L,
                state(LayerName.L1_5, LayerStatus.FAILED, false),
                state(LayerName.L2, LayerStatus.RUNNING, true));

        assertThat(service(layers).findSummaryStatuses(COMPANY, List.of(410L)))
                .containsExactly(new MeetingSummaryStatus(410L, SummaryStatus.FAILED));
    }

    @Test
    @DisplayName("⚠ 계층 일부만 DONE 인 회의는 완료가 아니다 — DIST 가 안 돌아 액션이 0건이다")
    void 부분_완료는_DONE이_아니다() {
        /*
         * 실패도 없고 도는 것도 없지만 끝까지 가지 않은 회의다(계층 사이에서 죽음).
         *
         * 이걸 DONE 으로 답하면 이 계약의 약속이 깨진다 — 「DONE 이면 액션이 최소 1건」인데
         * DIST 가 돌지 않았으므로 0건이다. D 화면은 pendingActionCount == 0 을 함께 보고
         * 「정상 완료」로 그린다. 하달된 것이 아무것도 없는 회의가 완료로 보인다.
         */
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(470L, done(LayerName.L1), done(LayerName.L1_5), done(LayerName.L2),
                done(LayerName.L3), done(LayerName.L3_5), done(LayerName.L4),
                done(LayerName.L5), done(LayerName.L6), done(LayerName.L7));  // DIST 만 없다

        assertThat(service(layers).findSummaryStatuses(COMPANY, List.of(470L)))
                .containsExactly(new MeetingSummaryStatus(470L, SummaryStatus.STALLED));
    }

    @Test
    @DisplayName("계층 기록이 없고 받아쓰기도 안 도는 회의는 NONE — 「실패」가 아니다")
    void 이력도_받아쓰기도_없으면_NONE() {
        /*
         * 지금 대다수 케이스다 — 자막 전송(CAP-11)이 붙기 전이라 발화 0건으로 분석이
         * 생략된다. 이걸 실패로 보여주면 정상적으로 아무 일도 없던 회의가 사고로 보인다.
         */
        assertThat(service(new FakeLayerStates()).findSummaryStatuses(COMPANY, List.of(420L)))
                .containsExactly(new MeetingSummaryStatus(420L, SummaryStatus.NONE));
    }

    @Test
    @DisplayName("받아쓰기가 도는 중이면 WAITING_TRANSCRIPT — 분석 시작 관문이 막고 있는 상태다")
    void 받아쓰기_중이면_대기로_답한다() {
        SttBlockRepository blocks = blocksWaitingFor(421L);

        List<MeetingSummaryStatus> result = new MeetingSummaryQueryService(
                (companyId, meetingId) -> true, new FakeLayerStates(), blocks)
                .findSummaryStatuses(COMPANY, List.of(421L));

        // 화면이 「요약 없음」이라고 말하면, 기다리면 되는 사용자가 잘못됐다고 읽는다.
        assertThat(result).containsExactly(new MeetingSummaryStatus(421L, SummaryStatus.WAITING_TRANSCRIPT));
    }

    @Test
    @DisplayName("⚠ 이미 분석된 회의는 받아쓰기를 묻지도 않는다 — 있는 요약을 「대기」로 덮으면 안 된다")
    void 분석된_회의는_받아쓰기를_묻지_않는다() {
        /*
         * 끝난 회의에 새 녹음이 붙어 미완 블록이 생기는 경우다. 그 회의에는 사람이 볼 요약이
         * 실제로 있으므로 DONE 을 유지해야 한다.
         *
         * 결과만 보지 않고 호출 자체를 검증하는 이유 — 물어보지 않으면 그 상태를 만들 수
         * 없다. if 분기로 막으면 나중에 그 분기가 지워질 수 있다.
         */
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(430L, fullPipelineDone());
        SttBlockRepository blocks = blocksWaitingFor(430L);

        List<MeetingSummaryStatus> result = new MeetingSummaryQueryService(
                (companyId, meetingId) -> true, layers, blocks).findSummaryStatuses(COMPANY, List.of(430L));

        assertThat(result).containsExactly(new MeetingSummaryStatus(430L, SummaryStatus.DONE));
        verify(blocks, never()).findMeetingsWithUnfinishedBlocks(anyList());
    }

    @Test
    @DisplayName("모든 회의에 항목이 나가고 입력 순서를 지킨다 — 호출자가 빠진 항목을 추측하면 안 된다")
    void 모든_회의에_항목이_나간다() {
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(441L, state(LayerName.L2, LayerStatus.FAILED, false));

        // 440·442 는 계층 기록이 없다 — 그래도 항목이 나와야 한다.
        List<MeetingSummaryStatus> result = service(layers)
                .findSummaryStatuses(COMPANY, List.of(442L, 441L, 440L));

        assertThat(result).containsExactly(
                new MeetingSummaryStatus(442L, SummaryStatus.NONE),
                new MeetingSummaryStatus(441L, SummaryStatus.FAILED),
                new MeetingSummaryStatus(440L, SummaryStatus.NONE));
    }

    @Test
    @DisplayName("남의 회사 회의는 항목이 아예 없다 — NONE 으로 채우면 「미시작」과 구분되지 않는다")
    void 남의_회사_회의는_항목이_없다() {
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(450L, fullPipelineDone());

        MeetingAccessPort access = (companyId, meetingId) -> companyId == COMPANY && meetingId == 450L;

        List<MeetingSummaryStatus> result = new MeetingSummaryQueryService(access, layers, blocksWaitingFor())
                .findSummaryStatuses(COMPANY, List.of(450L, 999L));

        assertThat(result).containsExactly(new MeetingSummaryStatus(450L, SummaryStatus.DONE));
    }

    @Test
    @DisplayName("중복 id 는 접힌다")
    void 중복_id는_접힌다() {
        FakeLayerStates layers = new FakeLayerStates();
        layers.put(460L, fullPipelineDone());

        assertThat(service(layers).findSummaryStatuses(COMPANY, List.of(460L, 460L)))
                .containsExactly(new MeetingSummaryStatus(460L, SummaryStatus.DONE));
    }

    @Test
    @DisplayName("입력이 비면 전체 조회도 아무것도 읽지 않는다")
    void 빈_입력은_전체_조회도_하지_않는다() {
        FakeLayerStates layers = new FakeLayerStates();

        assertThat(service(layers).findSummaryStatuses(COMPANY, List.of())).isEmpty();
        assertThat(service(layers).findSummaryStatuses(COMPANY, null)).isEmpty();
        assertThat(service(layers).findSummaryStatuses(null, List.of(1L))).isEmpty();

        assertThat(layers.batchCalls).isZero();
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    /* 회사 관문은 통과시키고 계층 상태만 보는 조립. 받아쓰기는 도는 것이 없다고 답한다. */
    private static MeetingSummaryQueryService service(FakeLayerStates layers) {
        return new MeetingSummaryQueryService((companyId, meetingId) -> true, layers, blocksWaitingFor());
    }

    /*
     * 받아쓰기 저장소만 Mockito 로 둔다 — 이 서비스가 그 포트에서 쓰는 메서드는 하나뿐이고,
     * 손으로 짜면 안 쓰는 메서드 열 개를 UnsupportedOperationException 으로 채워야 한다.
     * 호출 여부까지 검증해야 하는 테스트가 있어서(분석된_회의는_받아쓰기를_묻지_않는다) mock 이 맞다.
     */
    private static SttBlockRepository blocksWaitingFor(Long... waitingMeetingIds) {
        SttBlockRepository blocks = mock(SttBlockRepository.class);
        when(blocks.findMeetingsWithUnfinishedBlocks(anyList())).thenReturn(Set.of(waitingMeetingIds));
        return blocks;
    }

    private static LayerState done(LayerName layer) {
        return state(layer, LayerStatus.DONE, false);
    }

    /*
     * 열 계층 전부 DONE — 「정상 완료」의 유일한 모양이다.
     *
     * 목록을 손으로 적지 않는다. 계층이 하나 늘면 이 헬퍼가 자동으로 따라가고, 안 그러면
     * "완료" 픽스처가 옛 파이프라인을 가리킨 채 테스트만 통과한다 — 그게 이 PR 에서 고친
     * 버그의 모양이다(부분 완료를 DONE 으로 봤다).
     */
    private static LayerState[] fullPipelineDone() {
        return AnalysisOrchestrator.pipelineLayers().stream()
                .map(MeetingSummaryQueryServiceTest::done)
                .toArray(LayerState[]::new);
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
