package com.module06.backend.capture.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.MeetingSummaryView;
import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.application.result.ProcessingStatus;
import com.module06.backend.capture.application.result.ProcessingStatus.LayerProgress;
import com.module06.backend.capture.application.usecase.GetProcessingStatusUseCase;
import com.module06.backend.capture.application.usecase.GetSummaryUseCase;
import com.module06.backend.capture.application.usecase.RunAnalysisUseCase;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

/*
 * 분석 유스케이스 셋(ANLZ-01 · CAP-06 · ANLZ-03)의 구현이다.
 *
 * 오케스트레이션 자체는 {@link AnalysisOrchestrator} 가 갖고, 여기서는 **호출해도 되는지**만
 * 판정한다. 둘을 나눈 이유는 트리거가 늘어나기 때문이다 — 지금은 이 API 하나지만
 * MEET-08(회의 종료 자동 실행)과 SQS 워커가 같은 오케스트레이터를 부르게 된다.
 *
 * <h2>지금은 동기로 돈다</h2>
 * 명세의 202 는 SQS 를 전제한다. 이 슬라이스에는 큐가 없어 요청 스레드에서 그대로 돌린다.
 * 응답 status 는 **실제 결과**를 담으므로 거짓말은 아니다. SQS 가 붙는 자리는 이 메서드 하나이며,
 * 그때 이 클래스는 "메시지를 넣는다"로 바뀌고 워커가 오케스트레이터를 부른다.
 */
@Service
@RequiredArgsConstructor
public class AnalysisService implements RunAnalysisUseCase, GetProcessingStatusUseCase, GetSummaryUseCase {

    private final AnalysisOrchestrator orchestrator;
    private final AnalysisLayerRepository analysisLayerRepository;
    private final MeetingSummaryRepository meetingSummaryRepository;
    private final MeetingParticipantProvider participantProvider;

    /*
     * 회사 스코프 관문. 세 유스케이스가 **전부** 이걸 먼저 지난다.
     *
     * 각자 조회 조건에 회사를 끼워 넣는 방식으로 두면 언젠가 한 곳이 빠지고 그 API 만 조용히
     * 뚫린다 — CAP-06 이 실제로 그랬다(companyId 를 받아놓고 쓰지 않았다 · 이슈 #100).
     * SecurityConfig(기본 잠금)와 @PreAuthorize(역할)는 "이 회의가 그 사람 회사 것인가"를
     * 보지 않는다. 그 층이 여기다.
     */
    private final MeetingAccessGuard meetingAccessGuard;

    @Override
    public AnalysisOutcome run(long companyId, long meetingId, boolean force) {
        meetingAccessGuard.requireAccessible(companyId, meetingId);

        ProcessingStatus current = ProcessingStatus.of(layerProgress(meetingId));

        if (current.status() == ProcessingStatus.OverallStatus.RUNNING) {
            throw new BusinessException(CaptureErrorCode.ANALYSIS_ALREADY_RUNNING);
        }
        /*
         * force 없이 재실행을 막는 것이 비용 방어다. 같은 회의를 다시 돌리면 토큰이 그대로
         * 두 배가 된다 — 그래서 명세도 별도 권한과 확인 모달을 요구한다.
         *
         * ⚠ 판정을 오케스트레이터에 묻는다. ProcessingStatus.DONE("기록된 계층 행이 전부 DONE")을
         * 쓰면 안 된다 — 계층이 새로 붙었을 때 예전에 분석된 회의는 그 시절 행만 DONE 이라
         * "완료"로 읽히고, 새 계층은 force 없이는 영원히 돌지 않는다. 실제로 L2·L3 만 돌던
         * 회의가 L1.5·L3.5·L4 를 못 받는 상태였다. 완료 판정은 RUN_LAYERS 를 아는 쪽이 한다.
         */
        if (!force && orchestrator.isFullyAnalyzed(meetingId)) {
            throw new BusinessException(CaptureErrorCode.ANALYSIS_ALREADY_DONE);
        }

        List<AiLayerPort.Participant> participants = participantProvider.participantsOf(meetingId);

        // 테넌트 = 회사다. 계층에 그대로 넘어가 few-shot 조회 필터가 되므로 빠지면
        // 다른 회사 회의 발화가 프롬프트에 주입된다 — 정확도 문제가 아니라 유출이다.
        return orchestrator.run(companyId, companyId, meetingId, participants, force);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessingStatus getProcessingStatus(long companyId, long meetingId) {
        // analysis_layer 에는 company_id 가 없다. 그래서 조회 조건으로는 회사를 막을 수 없고,
        // 관문을 먼저 지나는 것이 유일한 방법이다(이 검증이 빠져 뚫려 있던 자리다).
        meetingAccessGuard.requireAccessible(companyId, meetingId);

        return ProcessingStatus.of(layerProgress(meetingId));
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingSummaryView getSummary(long companyId, long meetingId) {
        meetingAccessGuard.requireAccessible(companyId, meetingId);

        // 분석 전이면 빈 요약을 지어내지 않고 404 다. 빈 결과를 200 으로 돌려주면
        // 화면이 "요약할 내용이 없는 회의"로 읽고, 분석이 안 돌았다는 사실이 사라진다.
        return meetingSummaryRepository.findByMeeting(companyId, meetingId)
                .orElseThrow(() -> new BusinessException(CaptureErrorCode.SUMMARY_NOT_FOUND));
    }

    private List<LayerProgress> layerProgress(long meetingId) {
        return analysisLayerRepository.findStates(meetingId).stream()
                .map(state -> new LayerProgress(
                        state.layer(), state.status(), state.tokensIn(), state.tokensOut()))
                .toList();
    }
}
