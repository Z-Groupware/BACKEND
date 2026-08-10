package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.in.MeetingSummaryQueryPort;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository.LayerState;
import com.module06.backend.capture.application.port.out.MeetingAccessPort;
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
