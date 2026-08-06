package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.module06.backend.capture.application.port.out.AssignmentTupleRepository;
import com.module06.backend.capture.application.port.out.AssignmentTupleRepository.TupleRow;
import com.module06.backend.capture.application.port.out.ExtractTuplesResult;
import com.module06.backend.capture.application.port.out.GateResult;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.ItemView;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.MeetingSummaryView;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.TopicDecisions;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository.TopicView;
import com.module06.backend.capture.application.port.out.ResolveReferenceResult;
import com.module06.backend.capture.application.port.out.SegmentTopicsResult;
import com.module06.backend.capture.application.port.out.SummarizeTopicResult;
import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.GateStatus;
import com.module06.backend.capture.domain.model.GateVerdict;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.domain.model.ReferenceType;
import com.module06.backend.capture.domain.model.ResolvedReference;
import com.module06.backend.capture.domain.model.TopicSegment;
import com.module06.backend.capture.domain.model.Utterance;

/*
 * 분석 파이프라인의 오케스트레이터다. **계층 순서·잠금·상태 기록이 이 저장소의 몫**이고,
 * 계층 안에서 무엇을 판단하는지는 Python 몫이다(명세 「내부 API」 머리말).
 *
 * <h2>지금 도는 구간</h2>
 * L1.5(지시어 해소) → L2(주제 분할) → 주제마다 L3(정리) → meeting_summary·meeting_decision 저장
 * → 주제마다 L3.5(확정/논의 게이트) → gate_status 반영 → 주제마다 L4(tuple 추출)
 * → meeting_assignment_tuple 저장.
 * L1(화자 귀속) · L5(검증) · L6(모순 검사) · L7(자동확정 게이트)은 아직 붙지 않았다.
 * 계층을 하나 붙일 때 바꾸는 곳은 {@link #run} 안의 호출 순서와 {@link #RUN_LAYERS} 두 곳이고,
 * 잠금·상태 기록·토큰 집계는 {@link #runLayer} 가 공통으로 갖는다.
 *
 * <h2>왜 L3.5 가 L4 와 함께 붙는가</h2>
 * L4 를 단독으로 붙일 수 없다. Python 쪽 요청 스키마가 항목의 gateStatus 를
 * Literal["CONFIRMED"] 로 요구하므로 게이트를 지나지 않은 항목은 넣을 방법이 없고,
 * 전부 CONFIRMED 로 채워 보내는 것은 그 스키마가 막으려던 실패 그대로다 —
 * **아직 합의도 안 된 논의가 담당자에게 배정된다.**
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
 *
 * ⚠ L1.5 는 이 규칙의 경계선이다. 실패해도 L2 의 입력(원문 발화)은 온전하므로 "빈 입력 전파"가
 * 일어나지 않고, 계속 돌리면 요약까지는 나온다. 그래도 멈추는 쪽을 골랐다 — 이어서 돌리려면
 * "일부 계층이 실패한 DONE"이라는 결과 종류가 필요하고, 그건 ANLZ-01 응답과 CAP-06 계약을
 * 함께 바꾸는 일이다. 지금은 계층 상태(analysis_layer)로 어디서 멈췄는지 보이고 ANLZ-02 가
 * 이어서 돌린다. 이 판단을 뒤집을 거면 AnalysisOutcome 에 부분 성공을 먼저 만들 것.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    /* 이 오케스트레이터가 실제로 도는 계층. 계층을 붙이면 여기도 같이 늘린다 —
     * 빠뜨리면 "전부 완료" 판정이 새 계층을 안 보고 생략을 결정한다. */
    private static final Set<LayerName> RUN_LAYERS =
            Set.of(LayerName.L1_5, LayerName.L2, LayerName.L3, LayerName.L3_5, LayerName.L4);

    private final TranscriptRepository transcriptRepository;
    private final AnalysisLayerRepository analysisLayerRepository;
    private final MeetingSummaryRepository meetingSummaryRepository;
    private final AssignmentTupleRepository assignmentTupleRepository;
    private final MeetingDateProvider meetingDateProvider;
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
        if (!force && isFullyAnalyzed(meetingId)) {
            log.info("분석 생략 — 이미 완료된 회의다. meetingId={}", meetingId);
            return AnalysisOutcome.skipped("이미 분석이 완료된 회의입니다.");
        }

        List<Utterance> rawUtterances = transcriptRepository.findByMeetingOrderByOffset(meetingId);
        if (rawUtterances.isEmpty()) {
            // 발화가 없으면 계층을 부르지 않는다. 빈 입력으로 돌리면 빈 결과에 돈만 쓰고,
            // 그 빈 결과가 "분석 완료"로 기록돼 자막·STT 쪽 사고를 가린다.
            // (명세 ANLZ-01 의 SKIPPED_NO_CAPTION 과 같은 성질이다.)
            log.info("분석 생략 — 발화 0건 meetingId={}", meetingId);
            return AnalysisOutcome.skipped("발화가 없어 분석을 건너뛰었습니다.");
        }

        // ── L1.5 · 지시어 해소 ──────────────────────────────────────────────────
        /*
         * L2 **앞에** 돈다. 선행사는 주제 경계를 넘으므로("아까 그 얘기") 회의 전체를 한 번에
         * 보내고, 결과를 뒤 계층이 보는 발화에 반영한다. 순서를 뒤집으면 L4 가 이미 담당자를
         * 정한 뒤에 대명사가 풀린다 — 검토 사유 WRONG_ASSIGNEE 가 L1.5 로 귀속되는 이유다.
         */
        LayerOutcome<ResolveReferenceResult> resolved = runLayer(meetingId, LayerName.L1_5, sink -> {
            ResolveReferenceResult result = aiLayerPort.resolveReference(
                    // 대상 발화를 추리지 않고 전체를 넘긴다 — 지시어 후보를 고르는 코드가 아직 없고,
                    // 잘못 추리면 후보에서 빠진 지시어는 아예 풀릴 기회가 없다.
                    tenantId, meetingId, rawUtterances, List.of(), participants);
            sink.add(result.run());
            return new Accumulated<>(result, sink.spent());
        });
        if (!resolved.succeeded()) {
            return resolved.toAnalysisOutcome(LayerName.L1_5);
        }

        /*
         * 해소 결과를 발화에 주석으로 붙인 사본을 만든다. **DB 의 발화는 고치지 않는다** —
         * transcript_chunk 는 원본이고, 사람이 나중에 "정말 그렇게 말했나"를 확인하는 근거다.
         * 원문을 치환하면 그 근거가 사라지고, 잘못된 해소를 되돌릴 수도 없다.
         */
        List<Utterance> utterances = annotate(rawUtterances, resolved.value().references(), participants);

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

        // ── L3.5 · 확정/논의 게이트 (주제마다 한 번) + gate_status 반영 ──────────
        /*
         * L3 **저장 후에** 돈다. 판정을 meeting_decision.gate_status 에 적어야 하고, 저장 전에
         * 부르면 응답을 어느 행에 적용할지 임시 순번으로 맞춰야 한다 — 그 맞추기가 틀리면
         * A 항목의 판정이 B 항목에 저장되는데, 조회는 성공하므로 아무도 오류를 못 본다.
         */
        LayerOutcome<Void> gated = runLayer(meetingId, LayerName.L3_5, sink -> {
            Map<Integer, TopicView> savedBySeq = savedTopicsBySeq(companyId, meetingId);
            List<GateVerdict> verdicts = new ArrayList<>();
            int candidateCount = 0;

            for (TopicSegment topic : topics) {
                TopicView saved = savedBySeq.get(topic.topicSeq());
                if (saved == null) {
                    continue;
                }
                List<AiLayerPort.GateCandidate> candidates = gateCandidatesOf(saved);
                if (candidates.isEmpty()) {
                    // 근거 발화가 있는 항목이 하나도 없다. 부르면 판정할 대상이 없는 호출에
                    // 토큰만 쓴다. 이 주제의 항목은 미판정(NULL)으로 남아 L4 로 넘어가지 않는다.
                    continue;
                }
                candidateCount += candidates.size();

                GateResult result = aiLayerPort.gate(tenantId, meetingId, topic.topic(), candidates,
                        utterancesOf(topic, byId), participants);
                sink.add(result.run());
                verdicts.addAll(result.verdicts());
            }

            int applied = meetingSummaryRepository.applyGateVerdicts(meetingId, verdicts);
            if (applied != candidateCount) {
                /*
                 * 판정을 못 받은 항목이 있다. 오류로 올리지 않는다 — 그 항목은 미판정(NULL)으로
                 * 남아 L4 로 넘어가지 않으므로 안전한 방향이고, 여기서 회의 전체를 실패시키면
                 * 항목 하나 때문에 요약까지 못 쓰게 된다. 대신 수를 남겨 게이트 누락을 볼 수 있게 한다.
                 */
                log.warn("게이트 판정이 대상 수와 다르다 — meetingId={} 대상={} 반영={}",
                        meetingId, candidateCount, applied);
            }
            return new Accumulated<>(null, sink.spent());
        });
        if (!gated.succeeded()) {
            return gated.toAnalysisOutcome(LayerName.L3_5);
        }

        // ── L4 · assignment tuple 추출 (주제마다 한 번) + 산출물 저장 ────────────
        LayerOutcome<Integer> extracted = runLayer(meetingId, LayerName.L4, sink -> {
            /*
             * 게이트 반영 **후의 값을 다시 읽는다.** 위에서 받은 판정 목록을 그대로 쓰면
             * 되짚지 못해 반영되지 않은 판정까지 CONFIRMED 로 취급하게 된다 — DB 에는
             * 미판정으로 남은 항목이 tuple 로는 뽑히는, 두 곳이 서로 다른 말을 하는 상태다.
             */
            Map<Integer, TopicView> gatedBySeq = savedTopicsBySeq(companyId, meetingId);

            // 상대 표현("다음 주까지")의 기준일. 없으면 계층이 상대 표현을 계산하지 않는다 —
            // 오늘 날짜로 대체하지 않는다. 재실행은 회의 몇 주 뒤일 수도 있다.
            LocalDate meetingDate = meetingDateProvider.meetingDateOf(meetingId).orElse(null);
            if (meetingDate == null) {
                log.warn("회의 날짜를 읽지 못해 상대 기한을 계산하지 않는다. meetingId={}", meetingId);
            }

            List<TupleRow> rows = new ArrayList<>();
            for (TopicSegment topic : topics) {
                TopicView gatedTopic = gatedBySeq.get(topic.topicSeq());
                if (gatedTopic == null) {
                    continue;
                }
                List<ItemView> confirmed = confirmedItemsOf(gatedTopic);
                if (confirmed.isEmpty()) {
                    // 확정된 항목이 없는 주제는 부르지 않는다. 논의만 있었던 주제에서 배정을
                    // 뽑으려 하면 계층이 없는 담당자를 만들어내거나 빈 결과에 토큰만 쓴다.
                    continue;
                }

                ExtractTuplesResult result = aiLayerPort.extractTuples(
                        tenantId, meetingId, topic.topic(), toConfirmedItems(confirmed),
                        utterancesOf(topic, byId), participants, meetingDate);
                sink.add(result.run());

                rows.addAll(toTupleRows(result, confirmed, topic));
            }

            /*
             * rows 가 비어도 저장을 부른다 — 재실행에서 이전 tuple 을 지워야 한다.
             * 건너뛰면 지난 실행의 배정이 남아, 이번에 아무것도 확정되지 않은 회의가
             * 예전 배정을 그대로 들고 있게 된다.
             */
            assignmentTupleRepository.replace(companyId, meetingId, rows);
            return new Accumulated<>(rows.size(), sink.spent());
        });
        if (!extracted.succeeded()) {
            return extracted.toAnalysisOutcome(LayerName.L4);
        }

        log.info("분석 완료 — meetingId={} 주제 {}개 항목 {}건 tuple {}건",
                meetingId, decisions.size(),
                decisions.stream().mapToInt(d -> d.items().size()).sum(),
                extracted.value());
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
     *
     * ⚠ **공개해 두는 이유가 있다.** ANLZ-01 도 "이미 완료"를 판정해 409 를 주는데, 그쪽이
     * 자기만의 기준을 쓰면 두 판정이 갈린다. 실제로 갈렸던 모양이 이렇다 — 계층 상태 행이
     * 전부 DONE 이면 완료로 보는 기준을 쓰면, L2·L3 만 돌던 시절에 분석된 회의는 그 두 행이
     * DONE 이라 "완료"가 되고, 뒤에 붙은 L1.5·L3.5·L4 는 force 없이는 영원히 돌지 않는다.
     * 계층을 붙일 때마다 조용히 재발하므로 판정을 이 한 곳에만 둔다({@link #RUN_LAYERS}).
     */
    public boolean isFullyAnalyzed(long meetingId) {
        Set<LayerName> done = analysisLayerRepository.findStates(meetingId).stream()
                .filter(state -> state.status() == LayerStatus.DONE)
                .map(AnalysisLayerRepository.LayerState::layer)
                .collect(Collectors.toSet());
        return done.containsAll(RUN_LAYERS);
    }

    /*
     * 저장된 주제를 topicSeq 로 찾을 수 있게 만든다.
     *
     * 요약이 없으면 우리 버그다 — 바로 앞에서 L3 가 저장했다. 던져서 ORCHESTRATION_ERROR 로
     * 분류되게 둔다. 빈 목록으로 넘기면 게이트가 "판정 대상 없음"으로 조용히 지나가고,
     * 뒤이어 L4 도 아무것도 못 뽑는데 둘 다 DONE 으로 기록된다.
     */
    private Map<Integer, TopicView> savedTopicsBySeq(long companyId, long meetingId) {
        MeetingSummaryView view = meetingSummaryRepository.findByMeeting(companyId, meetingId)
                .orElseThrow(() -> new IllegalStateException(
                        "L3 가 저장한 요약을 찾을 수 없습니다. meetingId=" + meetingId));

        Map<Integer, TopicView> bySeq = new HashMap<>();
        view.topics().forEach(topic -> bySeq.put(topic.topicSeq(), topic));
        return bySeq;
    }

    /*
     * 게이트에 넘길 판정 대상을 고른다.
     *
     * 근거 발화가 없는 항목은 **제외한다.** Python 쪽 GateCandidate 가 non-nullable 로 받아
     * 실으면 422 이기도 하지만, 더 중요한 건 근거를 확인할 수 없는 항목이 확정으로 올라가지
     * 않는 쪽이 안전하다는 것이다. 제외된 항목은 gate_status 가 NULL 로 남는다.
     */
    private List<AiLayerPort.GateCandidate> gateCandidatesOf(TopicView topic) {
        return topic.items().stream()
                .filter(item -> item.id() != null && item.evidenceUtteranceId() != null)
                .map(item -> new AiLayerPort.GateCandidate(
                        item.id(), item.itemType(), item.content(), item.evidenceUtteranceId()))
                .toList();
    }

    /*
     * L4 로 넘길 항목을 고른다 — gate_status 가 CONFIRMED 인 것만이다.
     *
     * 미판정(NULL)과 DISCUSSED 를 같이 뺀다. 넘어가지 않는 건 같지만 이유가 다르고,
     * 그 구분은 gate_status 컬럼에 그대로 남아 있다.
     */
    private List<ItemView> confirmedItemsOf(TopicView topic) {
        return topic.items().stream()
                .filter(item -> GateStatus.fromNullable(item.gateStatus()) == GateStatus.CONFIRMED)
                .toList();
    }

    private List<AiLayerPort.ConfirmedItem> toConfirmedItems(List<ItemView> confirmed) {
        return confirmed.stream()
                .map(item -> new AiLayerPort.ConfirmedItem(
                        item.itemType(),
                        // 값으로 실어 보낸다. 여기서 CONFIRMED 를 지어 채우면 게이트가 뚫린다 —
                        // 위 필터를 지난 항목만 오므로 이 값은 항상 CONFIRMED 다.
                        GateStatus.CONFIRMED,
                        item.content(),
                        item.evidenceUtteranceId() != null
                                ? List.of(item.evidenceUtteranceId())
                                : List.of()))
                .toList();
    }

    /*
     * tuple 을 저장 행으로 옮기면서 **어느 확정 항목에서 나왔는지** 되짚는다.
     *
     * 되짚는 키는 근거 발화 id 다. L4 응답에 항목 키가 없고(계약이 그렇다), 대신 우리가 각
     * 항목의 근거 발화를 실어 보내면 Python 이 tuple 의 근거를 그 안에서만 고르게 좁힌다.
     * 그래서 근거 발화가 곧 연결선이 된다.
     *
     * 한 근거 발화에 확정 항목이 둘 이상 걸리면 **연결하지 않고 null 로 둔다.** 둘 중 하나를
     * 고르면 그 tuple 은 엉뚱한 결정에서 나온 것으로 기록되고, 사람이 검토할 때 근거 항목을
     * 눌러도 다른 내용이 나온다 — 근거를 모르는 것보다 나쁘다.
     */
    private List<TupleRow> toTupleRows(ExtractTuplesResult result, List<ItemView> confirmed,
                                       TopicSegment topic) {
        Map<Long, Long> decisionIdByEvidence = new HashMap<>();
        Set<Long> ambiguous = new java.util.HashSet<>();

        for (ItemView item : confirmed) {
            Long evidence = item.evidenceUtteranceId();
            if (evidence == null) {
                continue;
            }
            if (decisionIdByEvidence.put(evidence, item.id()) != null) {
                ambiguous.add(evidence);
            }
        }
        ambiguous.forEach(decisionIdByEvidence::remove);

        List<TupleRow> rows = new ArrayList<>();
        for (AssignmentTuple tuple : result.tuples()) {
            Long decisionId = decisionIdByEvidence.get(tuple.evidenceUtteranceId());
            if (decisionId == null) {
                log.info("tuple 의 근거 항목을 되짚지 못해 연결 없이 저장한다 — meetingId 주제={} 근거={}",
                        topic.topic(), tuple.evidenceUtteranceId());
            }
            rows.add(new TupleRow(tuple, decisionId, topic.topicSeq(), topic.topic(),
                    result.run().modelName(), result.run().promptVersion()));
        }
        return rows;
    }

    /*
     * L1.5 결과를 발화 사본에 주석으로 붙인다.
     *
     * **치환이 아니라 덧붙이기다.** "그거"를 해소된 표현으로 바꿔치면 원문이 사라져, 해소가
     * 틀렸을 때 뒤 계층이 무엇을 보고 판단했는지 되짚을 수 없다. 덧붙이면 최악이 모델이
     * 주석 문구를 인용하는 것이고, 그건 사람이 보면 바로 알아챈다.
     *
     * 원문에 surface 가 실제로 있는지 다시 확인한다. Python 후처리도 같은 검사를 하지만,
     * 계약이 갈렸을 때 엉뚱한 발화에 주석이 붙는 것보다 안 붙는 편이 낫다.
     */
    private List<Utterance> annotate(List<Utterance> utterances, List<ResolvedReference> references,
                                     List<AiLayerPort.Participant> participants) {
        if (references == null || references.isEmpty()) {
            return utterances;
        }

        Map<Long, String> nameByPersonId = new HashMap<>();
        participants.forEach(participant -> {
            if (participant.personId() != null) {
                nameByPersonId.put(participant.personId(), participant.name());
            }
        });

        Map<Long, List<ResolvedReference>> byUtteranceId = new HashMap<>();
        for (ResolvedReference reference : references) {
            if (reference.utteranceId() != null && reference.isAnnotatable()) {
                byUtteranceId.computeIfAbsent(reference.utteranceId(), id -> new ArrayList<>())
                        .add(reference);
            }
        }
        if (byUtteranceId.isEmpty()) {
            return utterances;
        }

        List<Utterance> annotated = new ArrayList<>(utterances.size());
        for (Utterance utterance : utterances) {
            List<ResolvedReference> matched = byUtteranceId.get(utterance.utteranceId());
            annotated.add(matched == null
                    ? utterance
                    : new Utterance(utterance.utteranceId(), utterance.speakerMemberId(),
                            utterance.startOffsetMs(), withAnnotations(utterance, matched, nameByPersonId)));
        }
        return annotated;
    }

    private String withAnnotations(Utterance utterance, List<ResolvedReference> references,
                                   Map<Long, String> nameByPersonId) {
        String text = utterance.text();
        if (text == null || text.isBlank()) {
            return text;
        }

        StringBuilder annotated = new StringBuilder(text);
        for (ResolvedReference reference : references) {
            if (!text.contains(reference.surface())) {
                log.debug("원문에 없는 지시 표현이라 주석을 붙이지 않는다 — utteranceId={} surface={}",
                        utterance.utteranceId(), reference.surface());
                continue;
            }
            String target = targetOf(reference, nameByPersonId);
            if (target == null) {
                continue;
            }
            annotated.append(" [지시어 \"").append(reference.surface()).append("\" → ")
                    .append(target).append(']');
        }
        return annotated.toString();
    }

    /*
     * 주석에 적을 대상을 고른다.
     *
     * PERSON 인데 명단 밖(resolvedPersonId=null)이면 이름을 적지 않는다. "사람을 가리키지만
     * 누군지 모른다"를 이름 없이 남겨야 L4 가 그걸 담당자로 쓰지 않는다 — 여기서 resolvedText 의
     * 표현("그 팀 분")을 이름처럼 적으면 담당자 후보로 읽힐 여지가 생긴다.
     */
    private String targetOf(ResolvedReference reference, Map<Long, String> nameByPersonId) {
        if (reference.referenceType() == ReferenceType.PERSON) {
            return reference.resolvedPersonId() != null
                    ? nameByPersonId.get(reference.resolvedPersonId())
                    : null;
        }
        return reference.resolvedText();
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
