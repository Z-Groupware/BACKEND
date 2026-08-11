package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.in.MeetingSummaryQueryPort;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository.LayerState;
import com.module06.backend.capture.application.port.out.MeetingAccessPort;
import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.application.result.ProcessingStatus;
import com.module06.backend.capture.application.result.ProcessingStatus.LayerProgress;
import com.module06.backend.capture.domain.model.LayerStatus;

/*
 * D(회의) 도메인이 묻는 요약 상태를 답한다.
 *
 * <h2>AnalysisService 에 넣지 않은 이유</h2>
 * 그쪽은 공개 API(ANLZ-01·02·03 · CAP-06)의 유스케이스 묶음이고, 관문이 **던지는** 전제로
 * 쓰여 있다(MeetingAccessGuard#requireAccessible). 이쪽은 도메인 간 배치 조회라 남의 회사
 * 항목을 **걸러내야** 한다 — 같은 클래스에 두면 두 규칙이 섞이고, 언젠가 배치 경로가
 * 던지거나 단건 경로가 조용히 통과한다.
 *
 * <h2>회의 단위로 접는 판정은 ProcessingStatus 를 그대로 쓴다</h2>
 * 계층 상태를 회의 하나의 상태로 접는 규칙(실패 우선 · 멈춘 RUNNING 은 실패로)이 이미 거기
 * 있고, CAP-06 이 화면에 보여주는 값도 그것이다. 여기서 따로 접으면 마이페이지 카드와 처리
 * 상태 화면이 같은 회의를 다르게 말한다.
 */
@Service
@RequiredArgsConstructor
public class MeetingSummaryQueryService implements MeetingSummaryQueryPort {

    private final MeetingAccessPort meetingAccessPort;
    private final AnalysisLayerRepository analysisLayerRepository;

    /*
     * 계층 기록이 없는 회의가 「미시작」인지 「받아쓰기 대기」인지 가르는 데만 쓴다.
     * 분석 시작 관문이 보는 것과 같은 값이다(AnalysisOrchestrator 의 countUnfinished).
     */
    private final SttBlockRepository sttBlockRepository;

    @Override
    @Transactional(readOnly = true)
    public List<StalledMeetingSummary> findStalledSummaries(Long companyId, List<Long> meetingIds) {
        if (companyId == null || meetingIds == null || meetingIds.isEmpty()) {
            return List.of();
        }

        // 남의 회사 회의를 먼저 떨어낸다. analysis_layer 에는 company_id 가 없어서(V5.6)
        // 이 단계를 건너뛰면 회사 경계가 아예 없는 조회가 된다.
        Set<Long> accessible = Set.copyOf(meetingAccessPort.filterInCompany(companyId, meetingIds));
        if (accessible.isEmpty()) {
            return List.of();
        }

        Map<Long, List<LayerState>> statesByMeeting =
                analysisLayerRepository.findStatesByMeetings(List.copyOf(accessible));

        List<StalledMeetingSummary> broken = new ArrayList<>();
        for (Map.Entry<Long, List<LayerState>> entry : statesByMeeting.entrySet()) {
            List<LayerState> states = entry.getValue();
            /*
             * 계층 행이 없는 회의는 애초에 키로 나오지 않는다(findStatesByMeetings 계약).
             * 그래서 여기 오는 것은 최소 한 번 분석이 시작된 회의뿐이다.
             */
            ProcessingStatus status = ProcessingStatus.of(states.stream()
                    .map(state -> new LayerProgress(state.layer(), state.status(),
                            state.tokensIn(), state.tokensOut(), state.stalled()))
                    .toList());

            if (status.status() != ProcessingStatus.OverallStatus.FAILED) {
                // DONE(정상 요약) · RUNNING(아직 도는 중)은 카드에 올리지 않는다.
                continue;
            }
            broken.add(new StalledMeetingSummary(entry.getKey(), isStalledRather(states)));
        }
        return broken;
    }

    /*
     * MEET-04 — 모든 회의의 상태를 답한다.
     *
     * <h2>받아쓰기 조회를 계층 기록이 없는 회의에만 던진다</h2>
     * 이미 분석된 회의는 미완 블록이 있어도 그 결과를 쓰지 않는다(WAITING_TRANSCRIPT 주석 —
     * NONE 만 쪼갠다). 그러면 전체 회의에 대해 물어보는 것은 버리는 값을 읽는 쿼리다.
     *
     * 조건을 if 로 두지 않고 **질문 대상 자체를 좁힌** 이유는, if 로 두면 나중에 누가 그
     * 분기를 지웠을 때 「DONE 인데 받아쓰기 대기」가 조용히 생기기 때문이다. 물어보지 않은
     * 값으로는 그 상태를 만들 수 없다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<MeetingSummaryStatus> findSummaryStatuses(Long companyId, List<Long> meetingIds) {
        if (companyId == null || meetingIds == null || meetingIds.isEmpty()) {
            return List.of();
        }

        Set<Long> accessible = Set.copyOf(meetingAccessPort.filterInCompany(companyId, meetingIds));
        if (accessible.isEmpty()) {
            return List.of();
        }

        /*
         * 입력 순서로 훑는다. filterInCompany 는 순서를 보장하지 않는다고 못 박혀 있고
         * (그쪽 주석), 화면이 회의 목록을 그린 순서와 응답 순서가 다르면 호출자가 id 로
         * 다시 맞춰야 한다 — 여기서 맞춰 주는 것이 싸다.
         */
        List<Long> targets = meetingIds.stream()
                .filter(Objects::nonNull)
                .filter(accessible::contains)
                .distinct()
                .toList();

        Map<Long, List<LayerState>> statesByMeeting = analysisLayerRepository.findStatesByMeetings(targets);

        // 계층 기록이 없는 회의만 골라 받아쓰기를 묻는다.
        List<Long> notStarted = targets.stream()
                .filter(id -> statesByMeeting.get(id) == null || statesByMeeting.get(id).isEmpty())
                .toList();
        Set<Long> waitingTranscript = notStarted.isEmpty()
                ? Set.of()
                : sttBlockRepository.findMeetingsWithUnfinishedBlocks(notStarted);

        List<MeetingSummaryStatus> statuses = new ArrayList<>();
        for (Long meetingId : targets) {
            List<LayerState> states = statesByMeeting.get(meetingId);
            if (states == null || states.isEmpty()) {
                statuses.add(new MeetingSummaryStatus(meetingId,
                        waitingTranscript.contains(meetingId)
                                ? SummaryStatus.WAITING_TRANSCRIPT
                                : SummaryStatus.NONE));
                continue;
            }
            statuses.add(new MeetingSummaryStatus(meetingId, summaryStatusOf(states)));
        }
        return statuses;
    }

    /*
     * 계층 상태를 화면이 쓰는 값 하나로 접는다.
     *
     * 접는 규칙은 ProcessingStatus 를 그대로 쓴다 — 여기서 따로 판정하면 CAP-06 처리 상태
     * 화면과 MEET-04 가 같은 회의를 다르게 말한다(클래스 주석과 같은 이유).
     *
     * ProcessingStatus 가 「멈춤」과 「실패」를 같은 FAILED 로 뭉갰기 때문에 그 안에서 다시
     * 원래 상태를 본다. 그 뭉개기는 "재개가 열려야 한다"는 판단이고, 화면 문구는 다른
     * 질문이다(isStalledRather 주석).
     */
    private static SummaryStatus summaryStatusOf(List<LayerState> states) {
        ProcessingStatus status = ProcessingStatus.of(states.stream()
                .map(state -> new LayerProgress(state.layer(), state.status(),
                        state.tokensIn(), state.tokensOut(), state.stalled()))
                .toList());

        return switch (status.status()) {
            case FAILED -> isStalledRather(states) ? SummaryStatus.STALLED : SummaryStatus.FAILED;
            case RUNNING -> SummaryStatus.PROCESSING;
            case DONE -> SummaryStatus.DONE;
            /*
             * 계층 목록이 비지 않았으므로 여기 오지 않는다(ProcessingStatus.of 는 비었을 때만
             * NOT_STARTED 를 낸다). 그래도 적어 두는 이유 — 나중에 그쪽 규칙이 바뀌어도
             * 「미시작」을 실패로 보여주지 않게 한다. NONE 은 위에서 이미 다뤘다.
             */
            case NOT_STARTED -> SummaryStatus.NONE;
        };
    }

    /*
     * 「중단」인가 「실패」인가.
     *
     * 실패한 계층이 하나라도 있으면 실패다(false). 실패가 없고 멈춘 계층만 있으면 중단이다(true).
     * 접는 쪽에서 둘을 같은 FAILED 로 뭉갰기 때문에(ProcessingStatus.of) 여기서 원래 상태를
     * 다시 본다 — 그 뭉개기는 "재개가 열려야 한다"는 판단이고, 화면 문구는 다른 질문이다.
     */
    private static boolean isStalledRather(List<LayerState> states) {
        boolean anyFailed = states.stream().anyMatch(state -> state.status() == LayerStatus.FAILED);
        return !anyFailed;
    }
}
