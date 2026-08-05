package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.TopicDecisions;
import com.module06.backend.capture.application.port.out.SegmentTopicsResult;
import com.module06.backend.capture.application.port.out.SummarizeTopicResult;
import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.domain.model.TopicSegment;
import com.module06.backend.capture.domain.model.Utterance;

/*
 * 분석 파이프라인의 오케스트레이터다. **계층 순서·잠금·상태 기록이 이 저장소의 몫**이고,
 * 계층 안에서 무엇을 판단하는지는 Python 몫이다(명세 「내부 API」 머리말).
 *
 * <h2>지금 도는 구간</h2>
 * L2(주제 분할) → 주제마다 L3(정리) → meeting_summary·meeting_decision 저장.
 * L1(화자 귀속) · L1.5(지시어) · L3.5(게이트) · L4(tuple) · L5(검증)는 아직 붙지 않았다.
 * 계층을 하나 붙일 때 바꾸는 곳은 {@link #run} 안의 호출 순서 한 곳이다 — 잠금·상태 기록·
 * 토큰 집계는 {@link #runLayer} 가 공통으로 갖는다.
 *
 * <h2>왜 계층마다 잠그나</h2>
 * 파이프라인 전체가 아니라 **계층 단위로** RUNNING 을 잡는다. 그래야 ANLZ-02(계층 재개)가
 * 성립한다 — 실패한 계층부터 이어서 돌리려면 앞 계층이 DONE 으로 남아 있어야 하고,
 * 잠금이 회의 단위면 "어디까지 됐는지"가 사라져 처음부터 다시 돌게 된다. 그만큼 재과금이다.
 *
 * <h2>실패를 삼키지 않는다</h2>
 * 계층이 실패하면 그 계층을 FAILED 로 남기고 **거기서 멈춘다.** 다음 계층으로 넘어가면
 * 입력이 빈 채로 도는데, 그 결과는 "산출물 없음"이 되어 품질 문제로 위장된다.
 * 이 파이프라인에서 가장 위험한 실패 방향이 그거다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    /* 이 오케스트레이터가 실제로 도는 계층. 계층을 붙이면 여기도 같이 늘린다 —
     * 빠뜨리면 "전부 완료" 판정이 새 계층을 안 보고 생략을 결정한다. */
    private static final Set<LayerName> RUN_LAYERS = Set.of(LayerName.L2, LayerName.L3);

    private final TranscriptRepository transcriptRepository;
    private final AnalysisLayerRepository analysisLayerRepository;
    private final MeetingSummaryRepository meetingSummaryRepository;
    private final AiLayerPort aiLayerPort;

    /*
     * 회의 하나를 분석한다.
     *
     * @param tenantId  테넌트(회사) 식별자. 계층에 그대로 넘어가 few-shot 조회 필터가 된다 —
     *                  빠지면 다른 회사 회의 발화가 프롬프트에 주입된다. 정확도 문제가 아니라 유출이다.
     * @param participants 닫힌 목록. 명단 밖 참석자를 나타내는 personId=null 항목을 포함해야 한다.
     */
    public AnalysisOutcome run(long tenantId, long companyId, long meetingId,
                               List<AiLayerPort.Participant> participants, boolean force) {

        /*
         * 이미 다 돌아간 회의를 다시 돌리지 않는다.
         *
         * ANLZ-01 도 같은 판정을 하지만(409), 이 오케스트레이터는 곧 MEET-08(회의 종료)과
         * SQS 워커도 부른다. 그 경로는 유스케이스의 검사를 지나지 않으므로, 중복 메시지 하나가
         * 회의 전체를 다시 태우게 된다 — SQS 는 at-least-once 라 중복은 언젠가 반드시 온다.
         * 계층 잠금은 "동시 실행"만 막고 "완료 후 재실행"은 막지 못한다.
         */
        if (!force && alreadyComplete(meetingId)) {
            log.info("분석 생략 — 이미 완료된 회의다. meetingId={}", meetingId);
            return AnalysisOutcome.skipped("이미 분석이 완료된 회의입니다.");
        }

        List<Utterance> utterances = transcriptRepository.findByMeetingOrderByOffset(meetingId);
        if (utterances.isEmpty()) {
            // 발화가 없으면 계층을 부르지 않는다. 빈 입력으로 돌리면 빈 결과에 돈만 쓰고,
            // 그 빈 결과가 "분석 완료"로 기록돼 자막·STT 쪽 사고를 가린다.
            // (명세 ANLZ-01 의 SKIPPED_NO_CAPTION 과 같은 성질이다.)
            log.info("분석 생략 — 발화 0건 meetingId={}", meetingId);
            return AnalysisOutcome.skipped("발화가 없어 분석을 건너뛰었습니다.");
        }

        Map<Long, Utterance> byId = new LinkedHashMap<>();
        utterances.forEach(utterance -> byId.put(utterance.utteranceId(), utterance));

        // ── L2 · 주제 분할 ───────────────────────────────────────────────────────
        LayerOutcome<SegmentTopicsResult> segmented = runLayer(meetingId, LayerName.L2, sink -> {
            SegmentTopicsResult result = aiLayerPort.segmentTopics(tenantId, meetingId, utterances);
            // 토큰을 먼저 sink 에 넣는다. 아래에서 던져도 이미 쓴 비용은 기록된다.
            sink.add(result.run());

            if (result.topics().isEmpty()) {
                // 발화는 있는데 주제가 하나도 안 나왔다. 여기서 "회의 전체를 한 주제"로 지어내면
                // 분할이 실패한 사실이 감춰지고, L3 는 회의 전체를 한 번에 요약하게 된다.
                //
                // 계층 안에서 던지는 이유: 밖에서 판정하면 runLayer 가 이미 L2 를 DONE 으로
                // 닫아버려서, 분석은 실패했는데 analysis_layer 는 "완료"라고 말하게 된다.
                // CAP-06 이 그 상태를 그대로 내려주므로 화면도 같이 거짓말한다.
                throw new AiLayerException("EMPTY_TOPICS", "주제 분할 결과가 비어 있습니다.", true);
            }
            return new Accumulated<>(result, sink.spent());
        });
        if (!segmented.succeeded()) {
            return segmented.toAnalysisOutcome(LayerName.L2);
        }

        List<TopicSegment> topics = segmented.value().topics();

        // ── L3 · 주제별 정리 (주제마다 한 번) + 산출물 저장 ──────────────────────
        List<TopicDecisions> decisions = new ArrayList<>();
        StringBuilder overview = new StringBuilder();

        LayerOutcome<Void> summarized = runLayer(meetingId, LayerName.L3, sink -> {
            for (TopicSegment topic : topics) {
                SummarizeTopicResult result = aiLayerPort.summarizeTopic(
                        tenantId, meetingId, topic.topicSeq(), topic.topic(),
                        utterancesOf(topic, byId), participants);

                decisions.add(new TopicDecisions(topic.topicSeq(), topic.topic(), result.items()));
                appendOverview(overview, topic, result.summary());
                // 주제마다 부르므로 토큰은 누적한다. 마지막 호출 값만 남기면 회의당 비용이
                // 주제 수만큼 과소 집계되고 QLTY-03 이 틀어진다.
                // sink 에 넣으므로 중간에 터져도 그때까지의 비용이 FAILED 기록에 남는다.
                sink.add(result.run());
            }

            /*
             * 저장을 계층 안에서 한다 — DONE 표시보다 **먼저** 끝나야 한다.
             *
             * 밖에서 저장하면 runLayer 가 L3 를 DONE 으로 닫은 뒤에 저장이 실패할 수 있고,
             * 그러면 "L3 완료인데 요약이 없는" 상태가 남는다. ANLZ-03 은 404 를 주고 CAP-06 은
             * 완료라고 말하는, 아무도 원인을 못 찾는 조합이다.
             */
            meetingSummaryRepository.replace(companyId, meetingId, overview.toString().strip(),
                    decisions, sink.spent().modelName(), sink.spent().promptVersion());

            return new Accumulated<>(null, sink.spent());
        });
        if (!summarized.succeeded()) {
            return summarized.toAnalysisOutcome(LayerName.L3);
        }

        log.info("분석 완료 — meetingId={} 주제 {}개 항목 {}건",
                meetingId, decisions.size(), decisions.stream().mapToInt(d -> d.items().size()).sum());
        return AnalysisOutcome.done(decisions.size());
    }

    /*
     * 계층 하나를 잠그고 → 돌리고 → 상태를 닫는다. 계층이 늘어도 이 흐름은 한 곳에 남는다.
     *
     * 잠금 실패는 오류가 아니다. 중복 수신이 걸러진 정상 동작이고, 그때 이미 다른 실행이
     * 같은 계층을 돌고 있으므로 여기서는 조용히 물러난다.
     */
    private <T> LayerOutcome<T> runLayer(long meetingId, LayerName layer, LayerCall<T> call) {
        if (!analysisLayerRepository.tryLock(meetingId, layer)) {
            log.info("계층 잠금 실패 — 이미 실행 중이다. meetingId={} layer={}", meetingId, layer.wireValue());
            return LayerOutcome.locked();
        }
        /*
         * 실패해도 그때까지 쓴 토큰이 남아야 한다. L3 는 주제마다 부르므로 5개 중 3번째에서
         * 터져도 앞의 2번은 이미 과금됐다 — 그걸 0 으로 기록하면 QLTY-03 이 실제보다 싼
         * 기준선을 보여주고, 그 숫자로 특화 모델 전환의 손익분기점을 계산하게 된다.
         */
        UsageSink sink = new UsageSink();
        try {
            Accumulated<T> accumulated = call.execute(sink);
            analysisLayerRepository.markDone(meetingId, layer, accumulated.run());
            return LayerOutcome.success(accumulated.value(), accumulated.run());
        } catch (AiLayerException e) {
            // 계층이 던진 분류를 그대로 남긴다. 여기서 다시 판정하면 Python 과 두 곳에서
            // 재시도 여부를 정하게 되고, 한쪽만 고쳐지는 상태가 만들어진다.
            log.warn("계층 실패 — meetingId={} layer={} code={} retryable={} 사용토큰={}/{}",
                    meetingId, layer.wireValue(), e.getErrorCode(), e.isRetryable(),
                    sink.spent().tokensIn(), sink.spent().tokensOut(), e);
            analysisLayerRepository.markFailed(
                    meetingId, layer, e.getErrorCode(), e.getMessage(), sink.spent());
            return LayerOutcome.failure(e.getErrorCode(), e.getMessage(), e.isRetryable());
        } catch (RuntimeException e) {
            // 우리 코드의 버그다. 제공자 실패와 섞으면 "재시도하면 되는 것"으로 오분류되어
            // 같은 버그를 세 번 돌린다.
            log.error("계층 실행 중 내부 오류 — meetingId={} layer={}", meetingId, layer.wireValue(), e);
            analysisLayerRepository.markFailed(
                    meetingId, layer, "ORCHESTRATION_ERROR", e.toString(), sink.spent());
            return LayerOutcome.failure("ORCHESTRATION_ERROR", e.toString(), false);
        }
    }

    /*
     * 이 오케스트레이터가 도는 계층이 전부 DONE 인가.
     *
     * 계층별 재개(ANLZ-02)는 아직 아니다 — L2 산출물을 따로 저장하지 않으므로 L3 만 이어서
     * 돌릴 수 없다. 그래서 지금은 "전부 완료면 생략, 아니면 처음부터"만 구분한다.
     */
    private boolean alreadyComplete(long meetingId) {
        Set<LayerName> done = analysisLayerRepository.findStates(meetingId).stream()
                .filter(state -> state.status() == LayerStatus.DONE)
                .map(AnalysisLayerRepository.LayerState::layer)
                .collect(Collectors.toSet());
        return done.containsAll(RUN_LAYERS);
    }

    /*
     * 주제에 넘길 발화를 고른다. TopicSegment.utteranceIds 를 쓴다 — 고유 구간(start·end)이
     * 아니라 **오버랩이 얹힌 목록**이어야 경계 발화를 L3 가 양쪽에서 볼 수 있다.
     *
     * 목록에 없는 id 는 조용히 건너뛴다. 그런 id 가 왔다면 L2 응답과 우리가 넘긴 발화가
     * 어긋난 것인데, 여기서 예외를 던지면 회의 전체가 실패한다. 계층 하나의 결과가 조금
     * 빈 것이 낫다.
     */
    private List<Utterance> utterancesOf(TopicSegment topic, Map<Long, Utterance> byId) {
        return topic.utteranceIds().stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /* 회의 전체 개요는 주제 요약을 이어 붙여 만든다. 회의 단위 요약 계층은 아직 없다. */
    private void appendOverview(StringBuilder overview, TopicSegment topic, String summary) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        if (!overview.isEmpty()) {
            overview.append("\n\n");
        }
        overview.append("· ").append(topic.topic()).append('\n').append(summary.strip());
    }

    @FunctionalInterface
    private interface LayerCall<T> {
        Accumulated<T> execute(UsageSink sink);
    }

    /*
     * 계층이 쓴 토큰을 모으는 자리. 성공·실패 양쪽에서 같은 값을 쓰려면 호출 바깥에 있어야 한다 —
     * 람다 안의 지역변수로 두면 예외가 나갈 때 그 값이 함께 사라진다(그게 원래 버그였다).
     */
    private static final class UsageSink {

        private LayerRun spent = LayerRun.empty();

        void add(LayerRun run) {
            if (run != null) {
                spent = spent.plus(run);
            }
        }

        LayerRun spent() {
            return spent;
        }
    }

    /* 계층 호출의 산출물 + 그 호출에 든 비용. */
    private record Accumulated<T>(T value, LayerRun run) {
    }

    /* 계층 하나의 실행 결과. 성공·이미 실행 중·실패 셋을 구분한다. */
    private record LayerOutcome<T>(T value, LayerRun run, boolean succeeded, boolean alreadyRunning,
                                   String errorCode, String errorMessage, boolean retryable) {

        static <T> LayerOutcome<T> success(T value, LayerRun run) {
            return new LayerOutcome<>(value, run, true, false, null, null, false);
        }

        /*
         * 이름이 컴포넌트(alreadyRunning)와 겹치면 안 된다 — 같은 이름의 무인자 메서드를
         * 레코드가 접근자 재정의로 보고, static 이면 "invalid accessor" 로 컴파일이 막힌다.
         */
        static <T> LayerOutcome<T> locked() {
            return new LayerOutcome<>(null, LayerRun.empty(), false, true, null, null, false);
        }

        static <T> LayerOutcome<T> failure(String errorCode, String message, boolean retryable) {
            return new LayerOutcome<>(null, LayerRun.empty(), false, false, errorCode, message, retryable);
        }

        AnalysisOutcome toAnalysisOutcome(LayerName layer) {
            return alreadyRunning
                    ? AnalysisOutcome.alreadyRunning(layer)
                    : AnalysisOutcome.failed(layer, errorCode, errorMessage, retryable);
        }
    }
}
