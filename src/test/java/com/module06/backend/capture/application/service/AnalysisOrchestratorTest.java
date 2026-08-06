package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.capture.application.port.out.AiLayerPort;
import com.module06.backend.capture.application.port.out.AnalysisLayerRepository;
import com.module06.backend.capture.application.port.out.AssignmentTupleRepository;
import com.module06.backend.capture.application.port.out.ExtractTuplesResult;
import com.module06.backend.capture.application.port.out.GateResult;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository;
import com.module06.backend.capture.application.port.out.ResolveReferenceResult;
import com.module06.backend.capture.application.port.out.SegmentTopicsResult;
import com.module06.backend.capture.application.port.out.SummarizeTopicResult;
import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.GateStatus;
import com.module06.backend.capture.domain.model.GateVerdict;
import com.module06.backend.capture.domain.model.ItemType;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.domain.model.ReferenceType;
import com.module06.backend.capture.domain.model.ResolvedReference;
import com.module06.backend.capture.domain.model.TopicSegment;
import com.module06.backend.capture.domain.model.Utterance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파이프라인 오케스트레이션 — 계층 순서 · 게이트 관문 · 산출물 저장.
 *
 * <p>실제 HTTP 없이 돈다. 계층 순서·잠금·상태 전이는 이 저장소의 로직이고, 그걸 검증하는 데
 * Gemini 크레딧이나 네트워크가 필요하면 아무도 테스트를 돌리지 않게 된다(AiLayerPort 주석).
 *
 * <p>가장 중요한 검증은 <b>게이트 관문</b>이다. L3.5 가 CONFIRMED 로 판정하지 않은 항목이
 * L4 로 넘어가면 아직 합의도 안 된 논의가 담당자에게 배정된다.
 */
class AnalysisOrchestratorTest {

    private static final long TENANT = 7L;
    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final LocalDate MEETING_DATE = LocalDate.of(2026, 8, 5);

    private static final List<AiLayerPort.Participant> PARTICIPANTS = List.of(
            new AiLayerPort.Participant(42L, "김서준"),
            new AiLayerPort.Participant(null, "명단 외"));

    @Test
    @DisplayName("CONFIRMED 항목만 L4 로 넘어간다 — DISCUSSED · 미판정은 제외된다")
    void 게이트를_통과한_항목만_tuple_추출로_넘어간다() {
        // 항목 3건: 하나는 CONFIRMED, 하나는 DISCUSSED, 하나는 게이트가 판정을 못 준 것.
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L),
                        item(ItemType.DISCUSSION, "가격 논의", 2L),
                        item(ItemType.BLOCKER, "인력 부족", 3L)),
                decisionIds -> List.of(
                        new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨"),
                        new GateVerdict(decisionIds.get(1), GateStatus.DISCUSSED, "결론 없음")));
        FakeTupleRepository tuples = new FakeTupleRepository();

        AnalysisOutcome outcome = orchestrator(summaries, tuples, ai).run(
                TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.DONE);

        // L4 요청에 실린 항목은 CONFIRMED 하나뿐이어야 한다.
        assertThat(ai.extractRequests).hasSize(1);
        assertThat(ai.extractRequests.get(0)).hasSize(1);
        assertThat(ai.extractRequests.get(0).get(0).content()).isEqualTo("로드맵 확정");
        // gateStatus 를 값으로 실어 보낸다 — 여기가 CONFIRMED 가 아니면 Python 이 422 로 거절한다.
        assertThat(ai.extractRequests.get(0).get(0).gateStatus()).isEqualTo(GateStatus.CONFIRMED);
    }

    @Test
    @DisplayName("확정 항목이 없는 회의는 L4 를 부르지 않는다 — 빈 호출에 토큰을 쓰지 않는다")
    void 확정된_항목이_없으면_L4를_부르지_않는다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DISCUSSION, "가격 논의", 1L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.DISCUSSED, "결론 없음")));
        FakeTupleRepository tuples = new FakeTupleRepository();

        AnalysisOutcome outcome = orchestrator(summaries, tuples, ai).run(
                TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.DONE);
        assertThat(ai.extractRequests).isEmpty();
        // 그래도 저장은 불렀다 — 재실행에서 이전 tuple 을 지워야 한다.
        assertThat(tuples.replaceCalls).isEqualTo(1);
        assertThat(tuples.saved).isEmpty();
    }

    @Test
    @DisplayName("근거 발화가 없는 항목은 게이트에 넣지 않는다 — 확인할 수 없는 항목은 확정되지 않는다")
    void 근거가_없는_항목은_게이트_대상이_아니다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                // evidence 가 null 인 항목 — 자막 폴백·사람이 직접 추가한 항목의 모양이다.
                List.of(item(ItemType.DECISION, "근거 없는 결정", null)),
                decisionIds -> List.of());
        FakeTupleRepository tuples = new FakeTupleRepository();

        orchestrator(summaries, tuples, ai).run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        // 판정 대상이 없으므로 게이트 호출 자체가 없다.
        assertThat(ai.gateRequests).isEmpty();
        assertThat(ai.extractRequests).isEmpty();
    }

    @Test
    @DisplayName("tuple 을 근거 발화로 확정 항목에 되짚어 저장한다")
    void tuple을_근거_항목에_연결해_저장한다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 11L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨")));
        ai.tuples = List.of(new AssignmentTuple(
                "로드맵 초안 작성", 42L, AssigneeSource.EXPLICIT_CALL, LocalDate.of(2026, 8, 12), 11L));
        FakeTupleRepository tuples = new FakeTupleRepository();

        orchestrator(summaries, tuples, ai).run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(tuples.saved).hasSize(1);
        AssignmentTupleRepository.TupleRow row = tuples.saved.get(0);
        assertThat(row.tuple().title()).isEqualTo("로드맵 초안 작성");
        // 근거 발화 11 을 가진 확정 항목의 id 로 연결돼야 한다.
        assertThat(row.decisionId()).isEqualTo(summaries.idOfEvidence(11L));
        assertThat(row.topicSeq()).isEqualTo(1);
        assertThat(row.modelName()).isEqualTo("gemini-flash");
    }

    @Test
    @DisplayName("L1.5 해소 결과를 뒤 계층 발화에 주석으로 붙인다 — 원문은 고치지 않는다")
    void 지시어_해소_결과가_뒤_계층_발화에_반영된다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨")));
        ai.references = List.of(
                new ResolvedReference(2L, "그거", ReferenceType.ARTIFACT, null, "로드맵 초안", 1L),
                new ResolvedReference(2L, "그분", ReferenceType.PERSON, 42L, null, 1L));

        orchestrator(summaries, new FakeTupleRepository(), ai).run(
                TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        // L1.5 는 원문을 본다.
        assertThat(ai.resolveUtterances).extracting(Utterance::text)
                .containsExactly("로드맵 정리합시다", "그거 그분한테 맡기죠");

        // L2 는 주석이 붙은 사본을 본다. 치환이 아니라 덧붙이기다 — 원문이 남아야 근거가 유지된다.
        assertThat(ai.segmentUtterances.get(1).text())
                .isEqualTo("그거 그분한테 맡기죠 [지시어 \"그거\" → 로드맵 초안] [지시어 \"그분\" → 김서준]");
        // 주석은 텍스트만 바꾼다. id 가 흔들리면 L2 가 돌려준 utteranceIds 와 어긋난다.
        assertThat(ai.segmentUtterances.get(1).utteranceId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("UNRESOLVED 기권은 발화에 적지 않는다 — 모델이 그 문구를 내용으로 인용한다")
    void 기권한_지시어는_주석을_붙이지_않는다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨")));
        ai.references = List.of(
                new ResolvedReference(2L, "그거", ReferenceType.UNRESOLVED, null, null, 1L),
                // PERSON 인데 명단 밖 — 이름을 적지 않아야 L4 가 담당자로 쓰지 않는다.
                new ResolvedReference(2L, "그분", ReferenceType.PERSON, null, "그 팀 분", 1L),
                // 원문에 없는 지시 표현 — 계약이 갈렸을 때 엉뚱한 자리에 붙는 것을 막는다.
                new ResolvedReference(2L, "저기", ReferenceType.ARTIFACT, null, "회의록", 1L));

        orchestrator(summaries, new FakeTupleRepository(), ai).run(
                TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(ai.segmentUtterances.get(1).text()).isEqualTo("그거 그분한테 맡기죠");
    }

    @Test
    @DisplayName("계층이 실패하면 거기서 멈추고 뒤 계층을 부르지 않는다")
    void 계층_실패는_파이프라인을_멈춘다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨")));
        ai.gateFailure = new AiLayerException("PROVIDER_UNAVAILABLE", "게이트 실패", true);
        FakeTupleRepository tuples = new FakeTupleRepository();
        FakeLayerRepository layers = new FakeLayerRepository();

        AnalysisOutcome outcome = orchestrator(summaries, tuples, ai, layers).run(
                TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.FAILED);
        assertThat(outcome.failedLayer()).isEqualTo(LayerName.L3_5);
        assertThat(outcome.retryable()).isTrue();
        // L4 로 넘어가지 않는다. 넘어가면 게이트 없는 항목으로 tuple 을 뽑는다.
        assertThat(ai.extractRequests).isEmpty();
        assertThat(tuples.replaceCalls).isZero();
        assertThat(layers.failed).containsKey(LayerName.L3_5);
    }

    @Test
    @DisplayName("새 계층이 안 돈 회의는 '완료'로 생략되지 않는다")
    void RUN_LAYERS에_새_계층이_들어가면_재실행_대상이다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨")));

        // L2·L3 만 DONE 인 회의 — L1.5·L3.5·L4 가 붙기 전에 분석된 회의의 모양이다.
        FakeLayerRepository layers = new FakeLayerRepository();
        layers.done.add(LayerName.L2);
        layers.done.add(LayerName.L3);

        AnalysisOutcome outcome = orchestrator(summaries, new FakeTupleRepository(), ai, layers).run(
                TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        // 생략되지 않고 다시 돈다. 생략되면 tuple 이 영원히 만들어지지 않는다.
        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.DONE);
        assertThat(ai.extractRequests).hasSize(1);
    }

    @Test
    @DisplayName("완료 판정은 RUN_LAYERS 기준이다 — 예전 계층만 DONE 이면 완료가 아니다")
    void 완료_판정은_RUN_LAYERS를_본다() {
        FakeLayerRepository layers = new FakeLayerRepository();
        AnalysisOrchestrator orchestrator = orchestrator(
                new FakeSummaryRepository(), new FakeTupleRepository(),
                new RecordingAiLayerPort(List.of(), decisionIds -> List.of()), layers);

        // L2·L3 만 DONE — 계층 상태 행만 보면 "전부 DONE"이라 완료로 읽히는 모양이다.
        layers.done.add(LayerName.L2);
        layers.done.add(LayerName.L3);
        assertThat(orchestrator.isFullyAnalyzed(MEETING)).isFalse();

        layers.done.add(LayerName.L1_5);
        layers.done.add(LayerName.L3_5);
        assertThat(orchestrator.isFullyAnalyzed(MEETING)).isFalse();

        layers.done.add(LayerName.L4);
        assertThat(orchestrator.isFullyAnalyzed(MEETING)).isTrue();
    }

    @Test
    @DisplayName("발화가 없으면 계층을 하나도 부르지 않는다")
    void 발화가_없으면_생략한다() {
        RecordingAiLayerPort ai = new RecordingAiLayerPort(List.of(), decisionIds -> List.of());

        AnalysisOutcome outcome = new AnalysisOrchestrator(
                meetingId -> List.of(), new FakeLayerRepository(), new FakeSummaryRepository(),
                new FakeTupleRepository(), meetingId -> Optional.of(MEETING_DATE), ai)
                .run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.SKIPPED);
        assertThat(ai.resolveCalls).isZero();
    }

    @Test
    @DisplayName("회의 날짜를 못 읽어도 분석은 돌고, 기준일 없이 L4 를 부른다")
    void 회의_날짜가_없어도_L4는_돈다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨")));

        AnalysisOutcome outcome = new AnalysisOrchestrator(
                meetingId -> utterances(), new FakeLayerRepository(), summaries,
                new FakeTupleRepository(), meetingId -> Optional.empty(), ai)
                .run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.DONE);
        // null 을 넘긴다 — 오늘 날짜로 대체하면 그럴듯하게 틀린 마감이 보드에 꽂힌다.
        assertThat(ai.meetingDates).containsExactly((LocalDate) null);
    }

    // ── 조립 ────────────────────────────────────────────────────────────────────

    private AnalysisOrchestrator orchestrator(FakeSummaryRepository summaries,
                                              FakeTupleRepository tuples,
                                              RecordingAiLayerPort ai) {
        return orchestrator(summaries, tuples, ai, new FakeLayerRepository());
    }

    private AnalysisOrchestrator orchestrator(FakeSummaryRepository summaries,
                                              FakeTupleRepository tuples,
                                              RecordingAiLayerPort ai,
                                              FakeLayerRepository layers) {
        return new AnalysisOrchestrator(
                meetingId -> utterances(), layers, summaries, tuples,
                meetingId -> Optional.of(MEETING_DATE), ai);
    }

    private static List<Utterance> utterances() {
        return List.of(
                new Utterance(1L, 42L, 0, "로드맵 정리합시다"),
                new Utterance(2L, null, 5_000, "그거 그분한테 맡기죠"));
    }

    private static com.module06.backend.capture.domain.model.TopicItem item(
            ItemType type, String content, Long evidenceUtteranceId) {
        return new com.module06.backend.capture.domain.model.TopicItem(
                type, content, "그렇게 분류한 근거", evidenceUtteranceId);
    }

    // ── 가짜 구현 ───────────────────────────────────────────────────────────────

    /*
     * 계층 호출을 기록하는 AiLayerPort. 무엇을 실어 보냈는지가 검증 대상이다 —
     * 게이트를 지나지 않은 항목이 L4 요청에 섞이는지는 요청 본문으로만 확인할 수 있다.
     */
    private static final class RecordingAiLayerPort implements AiLayerPort {

        private static final LayerRun RUN = new LayerRun(100, 20, "gemini-flash", "v1");

        private final List<com.module06.backend.capture.domain.model.TopicItem> l3Items;
        private final java.util.function.Function<List<Long>, List<GateVerdict>> verdicts;

        private List<ResolvedReference> references = List.of();
        private List<AssignmentTuple> tuples = List.of();
        private AiLayerException gateFailure;

        private int resolveCalls;
        private List<Utterance> resolveUtterances = List.of();
        private List<Utterance> segmentUtterances = List.of();
        private final List<List<GateCandidate>> gateRequests = new ArrayList<>();
        private final List<List<ConfirmedItem>> extractRequests = new ArrayList<>();
        private final List<LocalDate> meetingDates = new ArrayList<>();

        /* 게이트 판정은 저장된 항목의 id 를 알아야 만들 수 있어 함수로 받는다. */
        private RecordingAiLayerPort(List<com.module06.backend.capture.domain.model.TopicItem> l3Items,
                                     java.util.function.Function<List<Long>, List<GateVerdict>> verdicts) {
            this.l3Items = l3Items;
            this.verdicts = verdicts;
        }

        @Override
        public ResolveReferenceResult resolveReference(long tenantId, long meetingId, List<Utterance> utterances,
                                                       List<Long> targetUtteranceIds, List<Participant> participants) {
            resolveCalls++;
            resolveUtterances = List.copyOf(utterances);
            // 계약: null 이 아니라 빈 리스트여야 한다(pydantic 이 list 자리의 None 을 422 로 거절한다).
            assertThat(targetUtteranceIds).isNotNull();
            return new ResolveReferenceResult(references, RUN);
        }

        @Override
        public SegmentTopicsResult segmentTopics(long tenantId, long meetingId, List<Utterance> utterances) {
            segmentUtterances = List.copyOf(utterances);
            return new SegmentTopicsResult(
                    List.of(new TopicSegment(1, "제품 로드맵", 1L, 2L, List.of(1L, 2L))), RUN);
        }

        @Override
        public SummarizeTopicResult summarizeTopic(long tenantId, long meetingId, int topicSeq, String topic,
                                                   List<Utterance> utterances, List<Participant> participants) {
            return new SummarizeTopicResult("주제 요약", l3Items, RUN);
        }

        @Override
        public GateResult gate(long tenantId, long meetingId, String topic, List<GateCandidate> candidates,
                               List<Utterance> utterances, List<Participant> participants) {
            if (gateFailure != null) {
                throw gateFailure;
            }
            gateRequests.add(List.copyOf(candidates));
            return new GateResult(
                    verdicts.apply(candidates.stream().map(GateCandidate::decisionId).toList()), RUN);
        }

        @Override
        public ExtractTuplesResult extractTuples(long tenantId, long meetingId, String topic,
                                                 List<ConfirmedItem> items, List<Utterance> utterances,
                                                 List<Participant> participants, LocalDate meetingDate) {
            extractRequests.add(List.copyOf(items));
            meetingDates.add(meetingDate);
            return new ExtractTuplesResult(tuples, RUN);
        }
    }

    /*
     * meeting_summary·meeting_decision 을 흉내낸다. **id 를 붙여 돌려주는 것**이 요점이다 —
     * 게이트 판정을 그 id 로 되짚고, L4 는 되짚힌 gate_status 만 본다.
     */
    private static final class FakeSummaryRepository implements MeetingSummaryRepository {

        private final Map<Long, ItemView> byId = new LinkedHashMap<>();
        private final Map<Long, Integer> topicSeqById = new LinkedHashMap<>();
        private final Map<Integer, String> topicNameBySeq = new LinkedHashMap<>();
        private long nextId = 1_000L;

        @Override
        public void replace(long companyId, long meetingId, String overview, List<TopicDecisions> topics,
                            String modelName, String promptVersion) {
            byId.clear();
            topicSeqById.clear();
            topicNameBySeq.clear();

            for (TopicDecisions topic : topics) {
                topicNameBySeq.put(topic.topicSeq(), topic.topic());
                for (com.module06.backend.capture.domain.model.TopicItem item : topic.items()) {
                    long id = nextId++;
                    // 저장 시점의 gate_status 는 항상 null 이다 — 게이트가 아직 안 돌았다.
                    byId.put(id, new ItemView(id, item.itemType(), item.content(), item.reason(),
                            item.evidenceUtteranceId(), null));
                    topicSeqById.put(id, topic.topicSeq());
                }
            }
        }

        @Override
        public int applyGateVerdicts(long meetingId, List<GateVerdict> verdicts) {
            int applied = 0;
            for (GateVerdict verdict : verdicts) {
                ItemView existing = byId.get(verdict.decisionId());
                if (existing == null || verdict.gateStatus() == null) {
                    continue;
                }
                byId.put(existing.id(), new ItemView(existing.id(), existing.itemType(), existing.content(),
                        existing.reason(), existing.evidenceUtteranceId(), verdict.gateStatus().name()));
                applied++;
            }
            return applied;
        }

        @Override
        public Optional<MeetingSummaryView> findByMeeting(long companyId, long meetingId) {
            Map<Integer, List<ItemView>> grouped = new LinkedHashMap<>();
            byId.forEach((id, item) ->
                    grouped.computeIfAbsent(topicSeqById.get(id), seq -> new ArrayList<>()).add(item));

            List<TopicView> topics = grouped.entrySet().stream()
                    .map(entry -> new TopicView(entry.getKey(),
                            topicNameBySeq.get(entry.getKey()), entry.getValue()))
                    .toList();
            return Optional.of(new MeetingSummaryView("개요", topics));
        }

        private Long idOfEvidence(long evidenceUtteranceId) {
            return byId.values().stream()
                    .filter(item -> item.evidenceUtteranceId() != null)
                    .filter(item -> evidenceUtteranceId == item.evidenceUtteranceId())
                    .map(ItemView::id)
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static final class FakeTupleRepository implements AssignmentTupleRepository {

        private final List<TupleRow> saved = new ArrayList<>();
        private int replaceCalls;

        @Override
        public void replace(long companyId, long meetingId, List<TupleRow> rows) {
            replaceCalls++;
            saved.clear();
            saved.addAll(rows);
        }
    }

    private static final class FakeLayerRepository implements AnalysisLayerRepository {

        private final java.util.Set<LayerName> done = new java.util.HashSet<>();
        private final Map<LayerName, String> failed = new LinkedHashMap<>();

        @Override
        public boolean tryLock(long meetingId, LayerName layer) {
            return true;
        }

        @Override
        public void markDone(long meetingId, LayerName layer, LayerRun run) {
            done.add(layer);
        }

        @Override
        public void markFailed(long meetingId, LayerName layer, String errorCode, String errorMessage,
                               LayerRun spent) {
            failed.put(layer, errorCode);
        }

        @Override
        public List<LayerState> findStates(long meetingId) {
            return done.stream()
                    .map(layer -> new LayerState(layer, LayerStatus.DONE, 0, 0))
                    .toList();
        }
    }
}
