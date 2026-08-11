package com.module06.backend.capture.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository;
import com.module06.backend.capture.application.port.out.SttBlockRepository;
import com.module06.backend.capture.application.port.out.SttBlockRepository.SttBlockView;
import com.module06.backend.capture.application.port.out.SttGapRepository;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.MeetingSummaryView;
import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.application.result.ProcessingStatus;
import com.module06.backend.capture.application.result.ProcessingStatus.LayerProgress;
import com.module06.backend.capture.application.usecase.GetProcessingStatusUseCase;
import com.module06.backend.capture.application.usecase.GetSummaryUseCase;
import com.module06.backend.capture.application.usecase.ResumeAnalysisUseCase;
import com.module06.backend.capture.application.usecase.RunAnalysisUseCase;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.domain.model.SttBlockStatus;
import com.module06.backend.capture.domain.model.SttProgress;
import com.module06.backend.capture.exception.CaptureErrorCode;
import com.module06.backend.global.exception.BusinessException;

import java.util.Set;

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
public class AnalysisService implements RunAnalysisUseCase, GetProcessingStatusUseCase, GetSummaryUseCase,
        ResumeAnalysisUseCase {

    private final AnalysisOrchestrator orchestrator;
    private final AnalysisLayerRepository analysisLayerRepository;
    private final MeetingSummaryRepository meetingSummaryRepository;
    private final MeetingParticipantProvider participantProvider;

    /*
     * CAP-06 이 구멍과 "확인했는가"를 함께 답한다. 둘을 따로 두면 화면이 빈 배열을
     * "구멍 없음"으로 읽는다 — 그게 이 필드가 존재하는 이유다(ProcessingStatus 주석).
     */
    private final SttGapRepository sttGapRepository;
    private final SttBlockRepository sttBlockRepository;

    /* 남은 시간 추정의 기준 시각. 프로젝트 전체에 Clock 빈이 하나뿐이라 타입으로 주입된다. */
    private final Clock clock;

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

        List<ProcessingStatus.Gap> gaps = sttGapRepository.findByMeeting(meetingId).stream()
                .map(gap -> new ProcessingStatus.Gap(gap.startOffsetMs(), gap.endOffsetMs(),
                        gap.reason(), gap.mentionedNames(), gap.keywords()))
                .toList();

        /*
         * 블록을 **한 번만** 읽는다. 진행도(blocks)·남은 시간·gapsChecked 가 전부 같은 목록에서
         * 나온다 — 각자 조회하면 세 값이 서로 다른 순간의 스냅샷을 보고, 화면에 "전부 DONE 인데
         * 남은 시간이 90초"처럼 서로 모순되는 조합이 나간다.
         */
        List<SttBlockView> blocks = sttBlockRepository.findByMeeting(meetingId);
        SttProgress progress = SttProgress.of(
                blocks.stream()
                        .map(block -> new SttProgress.BlockTiming(block.status(), block.audioMs(),
                                block.startedAt(), block.finishedAt()))
                        .toList(),
                LocalDateTime.now(clock));

        return ProcessingStatus.of(layerProgress(meetingId), gaps, gapsChecked(blocks), progress);
    }

    /*
     * 그 빈 배열이 **확인 결과인가.**
     *
     * <h2>받아쓰기가 끝나야 확인된 것이다</h2>
     * 블록이 아직 QUEUED·RUNNING 이면 그 구간은 실패할 수도 있다 — 지금 구멍이 없다고 답하면
     * 사람이 그걸 "구멍 없음"으로 읽고 분배를 확정한다. 그래서 미완 블록이 하나라도 있으면
     * false 다. 분석 시작 관문(countUnfinished)과 **같은 뜻의 집합**을 봐야 한다 — 갈리면
     * 분석은 시작됐는데 구멍은 확인 안 됐다고 답하는 상태가 생긴다.
     *
     * <h2>블록이 아예 없으면 false 다</h2>
     * 받아쓰기를 한 적이 없는 회의다. 0 == 0 이라고 true 를 주면 "확인했고 구멍이 없다"가 되는데,
     * 실제로는 확인할 대상 자체가 없었다. 확인하지 않은 것과 확인해서 없는 것은 다르다.
     *
     * ⚠ **여기서 true 는 「받아쓰기는 확인했다」는 뜻이다.** 청크 유실·조립 구멍
     * (UPLOAD_MISSING · ASSEMBLY_GAP)은 녹음 조각을 아는 cap 이 판정하고 RecordSttGapPort 로
     * 기록한다 — 그 경로가 붙기 전까지 그 종류의 구멍은 이 목록에 나타나지 않는다.
     * 자동 블록 트리거가 청크 구멍을 검증하지 않는다는 것이 PR #302 의 리뷰 지적이었고,
     * 그 답이 이 포트다.
     */
    private boolean gapsChecked(List<SttBlockView> blocks) {
        if (blocks.isEmpty()) {
            return false;
        }
        /*
         * 미완 판정을 **이미 읽은 목록에서** 한다. countUnfinished 를 따로 부르면 조회 두 번
         * 사이에 블록이 끝나 "blocks 는 미완인데 gapsChecked 는 true" 같은 조합이 나간다.
         *
         * 상태 집합은 SttBlockStatus 의 뜻을 그대로 따른다 — DONE·FAILED 만 끝난 상태이고
         * 나머지는 미완이다. 이 판정이 countUnfinished 와 갈리면 분석 시작 관문과 다른 답을
         * 내므로, 상태를 추가할 때 둘을 함께 봐야 한다.
         */
        return blocks.stream().allMatch(block ->
                block.status() == SttBlockStatus.DONE || block.status() == SttBlockStatus.FAILED);
    }

    /*
     * ANLZ-02 · 계층 재개.
     *
     * <h2>여기서 막는 것이 재개의 전부다</h2>
     * 오케스트레이터는 "앞 계층을 부르지 않는다"만 한다. **앞 계층이 실제로 끝나 있는가**는
     * 여기서 본다 — 끝나지 않은 계층의 산출물을 되살리려 하면 빈 문맥으로 모델을 부르게 되고,
     * 그 빈 결과가 DONE 으로 기록돼 조회는 "분석 완료"라고 말한다.
     *
     * <h2>SUPERSEDED 를 DONE 으로 세지 않는다</h2>
     * 밀린 실행의 계층은 FAILED 로 남지 않는다(AnalysisOrchestrator#runLayer). 그 자리는
     * 애초에 상태 행이 없거나 이전 상태 그대로이므로, "DONE 인가"만 보는 이 검사가 자동으로
     * 걸러낸다 — 재개가 오래된 실행을 되살리지 않게 하려던 그 결정과 짝이다.
     */
    @Override
    public ResumeOutcome resume(long companyId, long meetingId, String resumeFromLayer) {
        meetingAccessGuard.requireAccessible(companyId, meetingId);

        /*
         * 도는 중이면 막는다 — run 과 같은 검사다(CodeRabbit PR #262).
         *
         * 없으면 재개가 새 runSeq 를 발급하고, 진행 중이던 실행은 다음 계층 잠금에서 SUPERSEDED
         * 로 물러난다(#134). 데이터는 안전하지만 **그 실행이 이미 태운 토큰이 버려진다** —
         * 재과금을 줄이려고 만든 API 가 정확히 반대로 동작하는 경로다.
         *
         * ⚠ 계층 파싱보다 **먼저** 본다. 자동 선택(계층 미지정)은 계층 상태를 읽어야 하는데,
         * 도는 중인 회의의 상태로 재개 지점을 고르면 아직 끝나지 않은 계층을 실패로 볼 수 있다.
         */
        if (ProcessingStatus.of(layerProgress(meetingId)).status() == ProcessingStatus.OverallStatus.RUNNING) {
            throw new BusinessException(CaptureErrorCode.ANALYSIS_ALREADY_RUNNING);
        }

        LayerName resumeFrom = resumeFromLayer == null
                ? firstBrokenLayer(meetingId)
                : parseLayer(resumeFromLayer);

        List<LayerName> reused = AnalysisOrchestrator.reusedLayersOf(resumeFrom);
        requireAllDone(meetingId, reused);

        List<AiLayerPort.Participant> participants = participantProvider.participantsOf(meetingId);

        /*
         * force 를 켜지 않는다. "이미 완료" 판정은 재개 경로에서 아예 지나지 않으므로
         * (AnalysisOrchestrator#run) 켤 이유가 없고, 켜면 그 뜻이 "재과금을 감수한다"로 읽힌다 —
         * 재개는 정확히 그 반대다.
         */
        AnalysisOutcome outcome = orchestrator.run(
                companyId, companyId, meetingId, participants, false, resumeFrom);

        return new ResumeOutcome(outcome, resumeFrom, reused);
    }

    /*
     * 재개 지점을 자동으로 고른다 — 파이프라인 순서로 **처음 깨진 계층**이다.
     *
     * <h2>왜 이 자리가 필요한가</h2>
     * 화면의 「다시 분석」 버튼은 계층 이름을 모른다. 그 값을 알아내려면 CAP-06 을 먼저 부르는
     * 왕복이 생기고, 그걸 피하려고 ANLZ-01(force)로 돌리면 이미 성공한 계층의 토큰이 다시
     * 나간다 — 재개 API 가 막으려던 바로 그것이다. 그래서 계층을 안 보내면 이쪽이 고른다.
     *
     * <h2>중단(stalled)도 깨진 것으로 본다</h2>
     * status 는 RUNNING 이지만 심장이 멈춘 계층이다(#177). ProcessingStatus 가 그것을 FAILED 로
     * 접는 것과 같은 판단이다 — 접는 쪽과 재개 지점을 고르는 쪽이 갈리면 화면은 "중단됨"이라
     * 말하는데 재개는 그 계층을 건너뛴다.
     *
     * <h2>앞 계층 검사를 대신하지 않는다</h2>
     * 고른 계층은 그대로 requireAllDone 을 지난다. 정상 경로에서는 앞 계층이 전부 DONE 이므로
     * 통과하지만, 중간 계층이 비어 있는 회의(계층이 새로 붙은 뒤 예전에 분석된 회의)는 거기서
     * 걸려야 한다 — 문맥 없이 부르는 것을 막는 검사를 여기서 우회하면 안 된다.
     */
    private LayerName firstBrokenLayer(long meetingId) {
        Map<LayerName, ProcessingStatus.LayerProgress> byLayer = layerProgress(meetingId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        ProcessingStatus.LayerProgress::layer, progress -> progress,
                        (first, second) -> first));

        for (LayerName layer : AnalysisOrchestrator.pipelineLayers()) {
            ProcessingStatus.LayerProgress progress = byLayer.get(layer);
            if (progress != null && (progress.status() == LayerStatus.FAILED || progress.stalled())) {
                return layer;
            }
        }
        /*
         * 깨진 계층이 없다. 전부 성공했거나 아직 한 번도 안 돌았거나인데, 둘 다 재개할 자리가
         * 없다 — 여기서 기본값(L1)으로 넘기면 성공한 회의를 통째로 다시 태우게 된다.
         */
        throw new BusinessException(CaptureErrorCode.RESUME_NOTHING_TO_RESUME);
    }

    private LayerName parseLayer(String wireValue) {
        if (wireValue == null || wireValue.isBlank()) {
            throw new BusinessException(CaptureErrorCode.RESUME_LAYER_UNKNOWN);
        }
        LayerName layer;
        try {
            layer = LayerName.fromWireValue(wireValue.trim());
        } catch (IllegalArgumentException e) {
            // 알 수 없는 계층을 기본값으로 넘기면 사용자가 의도한 곳이 아닌 데서 재개된다.
            throw new BusinessException(CaptureErrorCode.RESUME_LAYER_UNKNOWN);
        }
        if (!AnalysisOrchestrator.isPipelineLayer(layer)) {
            // 이름은 아는데 이 오케스트레이터가 돌리지 않는 계층이다.
            throw new BusinessException(CaptureErrorCode.RESUME_LAYER_UNKNOWN);
        }
        return layer;
    }

    private void requireAllDone(long meetingId, List<LayerName> required) {
        if (required.isEmpty()) {
            // 처음부터 재개(L1)다. 되살릴 앞 계층이 없으니 확인할 것도 없다.
            return;
        }
        Set<LayerName> done = analysisLayerRepository.findStates(meetingId).stream()
                .filter(state -> state.status() == LayerStatus.DONE)
                .map(AnalysisLayerRepository.LayerState::layer)
                .collect(java.util.stream.Collectors.toSet());

        if (!done.containsAll(required)) {
            throw new BusinessException(CaptureErrorCode.RESUME_PRECEDING_LAYER_NOT_DONE);
        }
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
                        state.layer(), state.status(), state.tokensIn(), state.tokensOut(),
                        state.stalled()))
                .toList();
    }
}
