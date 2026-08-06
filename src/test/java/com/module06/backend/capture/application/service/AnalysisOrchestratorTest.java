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
import com.module06.backend.capture.application.port.out.CaptionRepository;
import com.module06.backend.capture.application.port.out.ExtractTuplesResult;
import com.module06.backend.capture.application.port.out.GateResult;
import com.module06.backend.capture.application.port.out.LayerRun;
import com.module06.backend.capture.application.port.out.MeetingSummaryRepository;
import com.module06.backend.capture.application.port.out.ResolveReferenceResult;
import com.module06.backend.capture.application.port.out.SegmentTopicsResult;
import com.module06.backend.capture.application.port.out.SummarizeTopicResult;
import com.module06.backend.capture.application.port.out.TranscriptRepository;
import com.module06.backend.capture.application.port.out.VerifyTupleResult;
import com.module06.backend.capture.application.result.AnalysisOutcome;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.CaptionChunk;
import com.module06.backend.capture.domain.model.GateStatus;
import com.module06.backend.capture.domain.model.GateVerdict;
import com.module06.backend.capture.domain.model.ItemType;
import com.module06.backend.capture.domain.model.LayerName;
import com.module06.backend.capture.domain.model.LayerStatus;
import com.module06.backend.capture.domain.model.ReferenceType;
import com.module06.backend.capture.domain.model.ResolvedReference;
import com.module06.backend.capture.domain.model.TopicSegment;
import com.module06.backend.capture.domain.model.Utterance;
import com.module06.backend.capture.domain.model.VerifyVerdict;

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
    @DisplayName("후보로 보내지 않은 항목의 게이트 판정은 버린다 — 게이트를 통한 게이트 우회를 막는다")
    void 후보_밖_판정은_반영하지_않는다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        // 항목 둘: 하나는 근거가 있어 후보가 되고, 하나는 근거가 없어 **일부러 제외된다**.
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "근거 있는 결정", 1L),
                        item(ItemType.DECISION, "근거 없는 결정", null)),
                // 계층이 후보가 아닌 항목(근거 없는 결정)에 CONFIRMED 를 돌려준다.
                // 그대로 저장되면 근거를 확인할 수 없는 항목이 L4 로 넘어간다.
                decisionIds -> List.of(
                        new GateVerdict(999_999L, GateStatus.CONFIRMED, "요청에 없던 항목")));
        FakeTupleRepository tuples = new FakeTupleRepository();

        orchestrator(summaries, tuples, ai).run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        // 후보는 하나였는데 그 하나는 판정을 못 받았고, 온 판정은 후보 밖이라 버려진다.
        // 결과적으로 확정된 항목이 없으므로 L4 를 부르지 않는다.
        assertThat(ai.gateRequests).hasSize(1);
        assertThat(ai.extractRequests).isEmpty();
        assertThat(tuples.saved).isEmpty();
    }

    @Test
    @DisplayName("후보 밖 판정에 실제 항목 id 가 실려도 버린다")
    void 후보_밖_판정이_실존_항목을_가리켜도_버린다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "근거 있는 결정", 1L),
                        item(ItemType.DECISION, "근거 없는 결정", null)),
                // 후보(첫 항목) 대신 **같은 회의의 제외된 항목** id 로 CONFIRMED 를 돌려준다.
                // meetingId 스코프만 보는 검사로는 통과하는 모양이다 — 그게 이 테스트의 요점이다.
                decisionIds -> List.of(new GateVerdict(
                        summaries.idOfContent("근거 없는 결정"), GateStatus.CONFIRMED, "제외된 항목")));
        FakeTupleRepository tuples = new FakeTupleRepository();

        orchestrator(summaries, tuples, ai).run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(ai.extractRequests).isEmpty();
        assertThat(tuples.saved).isEmpty();
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
    @DisplayName("L1 이 화자를 이식하고, 뒤 계층은 이식된 화자를 본다")
    void L1이_이식한_화자가_뒤_계층에_보인다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨")));

        // 발화 2번(5~8초)은 화자가 null 이다. 참석자 전원(42)이 자막을 보내면 판정된다.
        FakeTranscriptRepository transcripts = new FakeTranscriptRepository(utterances());
        FakeCaptionRepository captions = new FakeCaptionRepository(
                new CaptionChunk(42L, 5_000, 8_000, new java.math.BigDecimal("-18.00")));

        AnalysisOutcome outcome = new AnalysisOrchestrator(
                transcripts, captions, new FakeLayerRepository(), summaries,
                new FakeTupleRepository(), meetingId -> Optional.of(MEETING_DATE),
                new SpeakerAttributionResolver(), ai)
                .run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.DONE);
        // 정본에 이식됐다.
        assertThat(transcripts.applied)
                .extracting(SpeakerAttributionResolver.Attribution::speakerMemberId)
                .containsOnly(42L);
        // 그리고 L2 가 보는 발화에 그 화자가 들어 있다 — 이식 후 다시 읽지 않으면 여기가 null 이다.
        assertThat(ai.segmentUtterances)
                .filteredOn(utterance -> utterance.utteranceId() == 2L)
                .extracting(Utterance::speakerMemberId)
                .containsExactly(42L);
    }

    @Test
    @DisplayName("자막이 없어도 분석은 끝까지 돈다 — CAP-11 미구현 상태의 정상 경로")
    void 자막이_없어도_파이프라인은_완주한다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨")));
        FakeTranscriptRepository transcripts = new FakeTranscriptRepository(utterances());

        AnalysisOutcome outcome = new AnalysisOrchestrator(
                transcripts, new FakeCaptionRepository(), new FakeLayerRepository(), summaries,
                new FakeTupleRepository(), meetingId -> Optional.of(MEETING_DATE),
                new SpeakerAttributionResolver(), ai)
                .run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        // 판정 0건이지만 실패가 아니다. 화자 미정은 정상 동작이고, 뒤 계층은 그대로 돈다.
        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.DONE);
        assertThat(transcripts.applied).isEmpty();
        assertThat(ai.extractRequests).hasSize(1);
    }

    @Test
    @DisplayName("재판정이 기권하면 예전 화자를 지운다 — 불확실해진 화자가 확정으로 굳으면 안 된다")
    void 기권한_발화의_예전_화자를_지운다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨")));

        // 발화 1 에는 예전 실행이 심어 둔 화자(42)가 남아 있다. 이번엔 자막이 없어 전원 기권이다.
        FakeTranscriptRepository transcripts = new FakeTranscriptRepository(utterances());

        AnalysisOutcome outcome = new AnalysisOrchestrator(
                transcripts, new FakeCaptionRepository(), new FakeLayerRepository(), summaries,
                new FakeTupleRepository(), meetingId -> Optional.of(MEETING_DATE),
                new SpeakerAttributionResolver(), ai)
                .run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.DONE);
        // 뒤 계층은 화자 없는 발화를 본다. 남아 있으면 L4 가 근거 없는 담당자를 확정한다.
        assertThat(ai.segmentUtterances).extracting(Utterance::speakerMemberId)
                .containsOnlyNulls();
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
        layers.done.add(LayerName.L4);
        // L1 이 빠져 있다. 계층을 붙일 때 이 판정을 갱신하지 않으면 여기서 걸린다.
        assertThat(orchestrator.isFullyAnalyzed(MEETING)).isFalse();

        layers.done.add(LayerName.L1);
        // L5 가 빠져 있다. 계층을 붙일 때마다 이 자리가 조용히 재발한다.
        assertThat(orchestrator.isFullyAnalyzed(MEETING)).isFalse();

        layers.done.add(LayerName.L5);
        assertThat(orchestrator.isFullyAnalyzed(MEETING)).isTrue();
    }

    @Test
    @DisplayName("발화가 없으면 계층을 하나도 부르지 않는다")
    void 발화가_없으면_생략한다() {
        RecordingAiLayerPort ai = new RecordingAiLayerPort(List.of(), decisionIds -> List.of());

        AnalysisOutcome outcome = new AnalysisOrchestrator(
                new FakeTranscriptRepository(List.of()), new FakeCaptionRepository(),
                new FakeLayerRepository(), new FakeSummaryRepository(), new FakeTupleRepository(),
                meetingId -> Optional.of(MEETING_DATE), new SpeakerAttributionResolver(), ai)
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
                new FakeTranscriptRepository(utterances()), new FakeCaptionRepository(),
                new FakeLayerRepository(), summaries, new FakeTupleRepository(),
                meetingId -> Optional.empty(), new SpeakerAttributionResolver(), ai)
                .run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.DONE);
        // null 을 넘긴다 — 오늘 날짜로 대체하면 그럴듯하게 틀린 마감이 보드에 꽂힌다.
        assertThat(ai.meetingDates).containsExactly((LocalDate) null);
    }

    // ── L5 · 관점 다변화 검증 ───────────────────────────────────────────────────

    @Test
    @DisplayName("L5 는 tuple 마다 한 번씩 돈다 — 주제 단위가 아니다")
    void L5는_tuple마다_한_번씩_돈다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L),
                        item(ItemType.DECISION, "일정 확정", 2L)),
                decisionIds -> decisionIds.stream()
                        .map(id -> new GateVerdict(id, GateStatus.CONFIRMED, "합의됨"))
                        .toList());
        // 한 주제에서 tuple 이 둘 나왔다. 주제 단위로 부르면 호출이 1회로 뭉친다.
        ai.tuples = List.of(
                new AssignmentTuple("로드맵 초안 작성", 42L, AssigneeSource.EXPLICIT_CALL, null, 1L),
                new AssignmentTuple("일정표 공유", 42L, AssigneeSource.FIRST_PERSON, null, 2L));
        FakeTupleRepository tuples = new FakeTupleRepository();

        orchestrator(summaries, tuples, ai).run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(ai.verifyTargets).extracting(AssignmentTuple::title)
                .containsExactly("로드맵 초안 작성", "일정표 공유");
    }

    @Test
    @DisplayName("검증 결과를 해당 tuple 행에 적는다 — agree=false 는 검토 대상이다")
    void 검증_결과가_tuple_행에_반영된다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L),
                        item(ItemType.DECISION, "일정 확정", 2L)),
                decisionIds -> decisionIds.stream()
                        .map(id -> new GateVerdict(id, GateStatus.CONFIRMED, "합의됨"))
                        .toList());
        ai.tuples = List.of(
                new AssignmentTuple("로드맵 초안 작성", 42L, AssigneeSource.EXPLICIT_CALL, null, 1L),
                new AssignmentTuple("일정표 공유", 42L, AssigneeSource.FIRST_PERSON, null, 2L));
        // 두 번째 tuple 만 관점이 갈렸다 — 판정이 tuple 별로 따로 적히는지가 요점이다.
        ai.verifyResults = tuple -> "일정표 공유".equals(tuple.title())
                ? new VerifyTupleResult(false, List.of("assigneeCandidatePersonId"),
                        VerifyVerdict.REJECT, "근거 발화에 담당자 지목이 없음", RecordingAiLayerPort.RUN)
                : new VerifyTupleResult(true, List.of(), VerifyVerdict.ACCEPT, "확인됨",
                        RecordingAiLayerPort.RUN);
        FakeTupleRepository tuples = new FakeTupleRepository();

        orchestrator(summaries, tuples, ai).run(TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        AssignmentTupleRepository.TupleVerification agreed = tuples.verificationOfTitle("로드맵 초안 작성");
        assertThat(agreed.agree()).isTrue();
        assertThat(agreed.disagreementFields()).isEmpty();

        AssignmentTupleRepository.TupleVerification disagreed = tuples.verificationOfTitle("일정표 공유");
        assertThat(disagreed.agree()).isFalse();
        assertThat(disagreed.disagreementFields()).containsExactly("assigneeCandidatePersonId");
        assertThat(disagreed.verdict()).isEqualTo(VerifyVerdict.REJECT);
        // 검증에 쓴 모델을 L4 의 것과 따로 남긴다 — 한 칸을 공유하면 추출 모델이 지워진다.
        assertThat(disagreed.modelName()).isEqualTo("gemini-flash");
    }

    @Test
    @DisplayName("검증에는 L4 에 넘긴 것과 같은 확정 항목을 넘긴다")
    void 검증_입력은_L4와_같다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L),
                        // 게이트를 통과하지 못할 항목 — 검증 재추출에도 실리면 안 된다.
                        item(ItemType.DISCUSSION, "예산은 논의만", 2L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨"),
                        new GateVerdict(decisionIds.get(1), GateStatus.DISCUSSED, "결론 없음")));
        ai.tuples = List.of(
                new AssignmentTuple("로드맵 초안 작성", 42L, AssigneeSource.EXPLICIT_CALL, null, 1L));

        orchestrator(summaries, new FakeTupleRepository(), ai).run(
                TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        // 입력이 갈리면 불일치가 관점 차이인지 입력 차이인지 구분되지 않는다.
        assertThat(ai.verifyItemRequests).hasSize(1);
        assertThat(ai.verifyItemRequests.get(0)).isEqualTo(ai.extractRequests.get(0));
        // 기준일도 같아야 한다 — 갈리면 dueDate 차이가 "관점이 갈렸다"로 기록된다.
        assertThat(ai.verifyMeetingDates).containsExactly(MEETING_DATE);
        // 확정된 것만 실렸다.
        assertThat(ai.verifyItemRequests.get(0))
                .extracting(AiLayerPort.ConfirmedItem::content)
                .containsExactly("로드맵 확정");
    }

    @Test
    @DisplayName("뽑힌 tuple 이 없으면 L5 를 부르지 않는다 — 실패가 아니라 대상 0건이다")
    void tuple이_없으면_L5를_부르지_않는다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨")));
        // L4 가 아무것도 못 뽑았다.
        ai.tuples = List.of();
        FakeLayerRepository layers = new FakeLayerRepository();

        AnalysisOutcome outcome = orchestrator(summaries, new FakeTupleRepository(), ai, layers).run(
                TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(ai.verifyTargets).isEmpty();
        // 계층은 DONE 으로 닫힌다. FAILED 로 두면 ANLZ-02 가 영원히 재시도한다.
        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.DONE);
        assertThat(layers.done).contains(LayerName.L5);
        assertThat(layers.failed).isEmpty();
    }

    @Test
    @DisplayName("L5 가 실패하면 분석이 L5 에서 멈춘다 — 검증 없이 완료로 넘어가지 않는다")
    void L5_실패는_분석을_멈춘다() {
        FakeSummaryRepository summaries = new FakeSummaryRepository();
        RecordingAiLayerPort ai = new RecordingAiLayerPort(
                List.of(item(ItemType.DECISION, "로드맵 확정", 1L)),
                decisionIds -> List.of(new GateVerdict(decisionIds.get(0), GateStatus.CONFIRMED, "합의됨")));
        ai.tuples = List.of(
                new AssignmentTuple("로드맵 초안 작성", 42L, AssigneeSource.EXPLICIT_CALL, null, 1L));
        // 두 관점이 모두 실패했을 때 Python 이 던지는 분류다.
        ai.verifyFailure = new AiLayerException("ALL_VIEWS_FAILED", "두 관점이 모두 실패", true);
        FakeTupleRepository tuples = new FakeTupleRepository();
        FakeLayerRepository layers = new FakeLayerRepository();

        AnalysisOutcome outcome = orchestrator(summaries, tuples, ai, layers).run(
                TENANT, COMPANY, MEETING, PARTICIPANTS, false);

        assertThat(outcome.status()).isEqualTo(AnalysisOutcome.Status.FAILED);
        assertThat(outcome.failedLayer()).isEqualTo(LayerName.L5);
        assertThat(outcome.retryable()).isTrue();
        assertThat(layers.failed).containsKey(LayerName.L5);
        // tuple 은 이미 저장돼 있고 지워지지 않는다 — 검증 실패는 추출 실패가 아니다.
        assertThat(tuples.saved).hasSize(1);
        // 다만 판정은 남지 않는다. 미검증(NULL)이 "검증에서 걸렸다"와 섞이면 안 된다.
        assertThat(tuples.verifications).isEmpty();
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
                new FakeTranscriptRepository(utterances()), new FakeCaptionRepository(),
                layers, summaries, tuples, meetingId -> Optional.of(MEETING_DATE),
                // 판정 로직은 순수 계산이라 가짜로 대체하지 않는다 — 실물을 넣어야
                // 오케스트레이터가 참석자 명단을 어떻게 넘기는지까지 함께 검증된다.
                new SpeakerAttributionResolver(), ai);
    }

    private static List<Utterance> utterances() {
        return List.of(
                new Utterance(1L, 42L, 0, 3_000, "로드맵 정리합시다"),
                new Utterance(2L, null, 5_000, 8_000, "그거 그분한테 맡기죠"));
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

        private final List<AssignmentTuple> verifyTargets = new ArrayList<>();
        private final List<List<ConfirmedItem>> verifyItemRequests = new ArrayList<>();
        private final List<LocalDate> verifyMeetingDates = new ArrayList<>();
        /* 기본은 두 관점 일치다. 불일치를 보려는 테스트만 이 함수를 바꾼다. */
        private java.util.function.Function<AssignmentTuple, VerifyTupleResult> verifyResults =
                tuple -> new VerifyTupleResult(true, List.of(), VerifyVerdict.ACCEPT, "근거 발화로 확인됨", RUN);
        private AiLayerException verifyFailure;

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

        @Override
        public VerifyTupleResult verifyTuple(long tenantId, long meetingId, String topic, AssignmentTuple tuple,
                                             List<ConfirmedItem> items, List<Utterance> utterances,
                                             List<Participant> participants, LocalDate meetingDate) {
            if (verifyFailure != null) {
                throw verifyFailure;
            }
            verifyTargets.add(tuple);
            verifyItemRequests.add(List.copyOf(items));
            verifyMeetingDates.add(meetingDate);
            return verifyResults.apply(tuple);
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

        private Long idOfContent(String content) {
            return byId.values().stream()
                    .filter(item -> content.equals(item.content()))
                    .map(ItemView::id)
                    .findFirst()
                    .orElseThrow();
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

    /*
     * 정본 저장소. L1 이 이식한 화자를 기록하고, 이식 후 재조회에서 그 값을 반영해 돌려준다 —
     * 오케스트레이터가 "이식 후 다시 읽는다"를 실제로 하는지 이 가짜가 드러낸다.
     */
    private static final class FakeTranscriptRepository implements TranscriptRepository {

        private final List<Utterance> utterances;
        private final List<SpeakerAttributionResolver.Attribution> applied = new ArrayList<>();

        private FakeTranscriptRepository(List<Utterance> utterances) {
            this.utterances = new ArrayList<>(utterances);
        }

        @Override
        public List<Utterance> findByMeetingOrderByOffset(long meetingId) {
            return List.copyOf(utterances);
        }

        /*
         * 실물과 같은 계약이다 — **이번 판정이 그 회의의 화자 상태 전부다.** 목록에 없는 발화는
         * 화자를 NULL 로 되돌린다. 덮어쓰기만 흉내내면 "기권했는데 예전 판정이 남는" 버그가
         * 이 가짜에서는 재현되지 않아 테스트가 통과해 버린다.
         */
        @Override
        public int applySpeakerAttributions(long meetingId, List<SpeakerAttributionResolver.Attribution> attributions) {
            applied.addAll(attributions);

            Map<Long, SpeakerAttributionResolver.Attribution> byUtteranceId = new LinkedHashMap<>();
            attributions.forEach(attribution -> byUtteranceId.put(attribution.utteranceId(), attribution));

            utterances.replaceAll(utterance -> {
                SpeakerAttributionResolver.Attribution attribution = byUtteranceId.get(utterance.utteranceId());
                return new Utterance(utterance.utteranceId(),
                        attribution != null ? attribution.speakerMemberId() : null,
                        utterance.startOffsetMs(), utterance.endOffsetMs(), utterance.text());
            });
            return attributions.size();
        }
    }

    /* CAP-11 이 미구현이라 기본값은 자막 0건이다 — 실제 운영 상태와 같다. */
    private static final class FakeCaptionRepository implements CaptionRepository {

        private final List<CaptionChunk> captions;

        private FakeCaptionRepository(CaptionChunk... captions) {
            this.captions = List.of(captions);
        }

        @Override
        public List<CaptionChunk> findByMeeting(long meetingId) {
            return captions;
        }
    }

    /*
     * meeting_assignment_tuple 을 흉내낸다. **저장하면서 id 를 붙이는 것**이 요점이다 —
     * L5 는 저장된 행의 id 로 판정을 되짚으므로, id 가 없으면 그 경로가 검증되지 않는다.
     */
    private static final class FakeTupleRepository implements AssignmentTupleRepository {

        private final List<TupleRow> saved = new ArrayList<>();
        private final Map<Long, StoredTuple> storedById = new LinkedHashMap<>();
        private final Map<Long, TupleVerification> verifications = new LinkedHashMap<>();
        private int replaceCalls;
        private long nextId = 9_000L;

        @Override
        public void replace(long companyId, long meetingId, List<TupleRow> rows) {
            replaceCalls++;
            saved.clear();
            saved.addAll(rows);

            // 교체이므로 이전 판정도 함께 사라진다 — 새 tuple 이 예전 검증 결과를 물려받으면
            // 검증받지 않은 배정이 검증된 것으로 보인다.
            storedById.clear();
            verifications.clear();
            for (TupleRow row : rows) {
                long id = nextId++;
                storedById.put(id, new StoredTuple(id, row.tuple(), row.topicSeq(), row.topic()));
            }
        }

        @Override
        public List<StoredTuple> findByMeeting(long companyId, long meetingId) {
            return List.copyOf(storedById.values());
        }

        @Override
        public int applyVerifications(long meetingId, List<TupleVerification> incoming) {
            int applied = 0;
            for (TupleVerification verification : incoming) {
                if (storedById.containsKey(verification.tupleId())) {
                    verifications.put(verification.tupleId(), verification);
                    applied++;
                }
            }
            return applied;
        }

        private TupleVerification verificationOfTitle(String title) {
            return storedById.values().stream()
                    .filter(stored -> title.equals(stored.tuple().title()))
                    .map(stored -> verifications.get(stored.id()))
                    .findFirst()
                    .orElseThrow();
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
