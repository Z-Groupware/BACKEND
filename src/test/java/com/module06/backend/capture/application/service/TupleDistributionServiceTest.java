package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import com.module06.backend.action.application.port.ActionDistributionPort;
import com.module06.backend.action.application.port.ActionDistributionPort.ActionDistributionItem;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.action.application.port.ActionDistributionPort.DistributeActionsCommand;
import com.module06.backend.action.application.port.ActionDistributionPort.DistributedAction;
import com.module06.backend.capture.application.port.out.AssignmentTupleRepository;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.GateSignals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * tuple → action 분배. 파이프라인 산출물이 <b>처음 A 밖으로 나가는 자리</b>다.
 *
 * <p>여기서 잘못되면 사람 보드에 액션이 두 번 꽂히거나, 검토 화면에서 배정이 통째로 사라진다.
 * 둘 다 사람이 손으로 되돌려야 하는 실패라 계약 위반을 던지는 것보다 나쁘다.
 *
 * <p>오케스트레이션(L7 뒤에 도는가 · DIST 상태가 닫히는가)은 AnalysisOrchestratorTest 가 본다.
 * 이 클래스는 <b>무엇을 실어 보내는가</b>만 본다.
 */
class TupleDistributionServiceTest {

    private static final long COMPANY = 7L;
    private static final long MEETING = 500L;
    private static final long PROJECT = 31L;

    @Test
    @DisplayName("회의의 프로젝트를 못 읽으면 던진다 — 조용히 0 을 주면 DIST 가 완료로 닫힌다")
    void 프로젝트를_모르면_던진다() {
        FakeTupleRepository tuples = new FakeTupleRepository(
                stored(1L, tuple("로드맵 초안 작성", 42L)));
        RecordingDistributionPort actions = new RecordingDistributionPort();

        /*
         * meeting.project_id 는 NOT NULL 이므로 이 값이 비었다는 것은 회의 행을 못 읽었다는
         * 뜻이다 — 분배할 것이 없는 정상 상태가 아니라 데이터 오류다. 0 을 돌려주면 DIST 가
         * DONE 으로 닫히고, 액션이 하나도 없는 회의가 "분석 완료"가 되어 재실행되지 않는다.
         */
        assertThatThrownBy(() -> service(tuples, actions, meetingId -> Optional.empty(), false)
                .distribute(COMPANY, MEETING, Map.of()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(actions.items).isEmpty();
        // 되짚을 것도 없다. tuple 은 대기실에 그대로 남는다.
        assertThat(tuples.applied).isEmpty();
    }

    @Test
    @DisplayName("L7 판정이 없는 tuple 은 게이트 신호를 비워 보낸다 — false 넷으로 채우지 않는다")
    void 게이트_판정이_없으면_신호를_싣지_않는다() {
        FakeTupleRepository tuples = new FakeTupleRepository(
                stored(1L, tuple("로드맵 초안 작성", 42L)));
        RecordingDistributionPort actions = new RecordingDistributionPort();

        // 판정 map 이 비어 있다 = L7 이 이 행을 보지 않았다.
        service(tuples, actions, meetingId -> Optional.of(PROJECT), false)
                .distribute(COMPANY, MEETING, Map.of());

        // null 이어야 한다. false 넷을 채우면 "게이트가 떨어뜨렸다"로 읽히는데,
        // 그건 "게이트를 지나지 않았다"와 다른 상태다.
        assertThat(actions.items.get(0).gateSignals()).isNull();
    }

    @Test
    @DisplayName("신호는 넷 다 통과했는데 모순으로 걸린 건이 사본에서 통과한 것처럼 보이지 않는다")
    void 게이트_신호와_판정을_함께_싣는다() {
        FakeTupleRepository tuples = new FakeTupleRepository(
                stored(1L, tuple("로드맵 초안 작성", 42L)));
        RecordingDistributionPort actions = new RecordingDistributionPort();

        // 신호 넷은 전부 통과. 그런데 L6 모순 때문에 자동확정은 아니다.
        Map<Long, AutoConfirmGate.Verdict> verdicts = Map.of(
                1L, new AutoConfirmGate.Verdict(new GateSignals(true, true, true, true), false));

        service(tuples, actions, meetingId -> Optional.of(PROJECT), false)
                .distribute(COMPANY, MEETING, verdicts);

        String signals = actions.items.get(0).gateSignals();
        assertThat(signals).contains("\"hasEvidence\":true")
                .contains("\"viewsAgree\":true")
                // 판정이 함께 있어야 사본만 보고도 "통과하지 못했다"를 알 수 있다.
                .contains("\"autoConfirmed\":false");
    }

    @Test
    @DisplayName("action.title(200)을 넘는 제목은 자른다 — 길이 하나 때문에 회의 전체가 막히면 안 된다")
    void 긴_제목은_자른다() {
        String longTitle = "가".repeat(250);
        FakeTupleRepository tuples = new FakeTupleRepository(
                stored(1L, tuple(longTitle, 42L)));
        RecordingDistributionPort actions = new RecordingDistributionPort();

        service(tuples, actions, meetingId -> Optional.of(PROJECT), false)
                .distribute(COMPANY, MEETING, Map.of());

        assertThat(actions.items.get(0).title()).hasSize(200);
    }

    @Test
    @DisplayName("담당자 판정 근거를 그대로 넘긴다 — 게이트 조건3 이 사본에서 사라지면 안 된다")
    void 담당자_판정_근거를_넘긴다() {
        FakeTupleRepository tuples = new FakeTupleRepository(
                stored(1L, new AssignmentTuple("회의록 정리", 42L, AssigneeSource.FIRST_PERSON,
                        LocalDate.of(2026, 8, 12), 9L)));
        RecordingDistributionPort actions = new RecordingDistributionPort();

        service(tuples, actions, meetingId -> Optional.of(PROJECT), false)
                .distribute(COMPANY, MEETING, Map.of());

        ActionDistributionItem item = actions.items.get(0);
        assertThat(item.assigneeSource())
                .isEqualTo(com.module06.backend.action.domain.model.AssigneeSource.FIRST_PERSON);
        // 기한은 있는 그대로 넘긴다 — 비어 있을 때만 C 가 프로젝트 마감일로 채운다.
        assertThat(item.dueDate()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(item.evidenceTranscriptId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("기한이 없으면 비워 보낸다 — 오늘 날짜로 채우면 그럴듯하게 틀린 마감이 꽂힌다")
    void 기한이_없으면_비워_보낸다() {
        FakeTupleRepository tuples = new FakeTupleRepository(
                stored(1L, tuple("로드맵 초안 작성", 42L)));
        RecordingDistributionPort actions = new RecordingDistributionPort();

        service(tuples, actions, meetingId -> Optional.of(PROJECT), false)
                .distribute(COMPANY, MEETING, Map.of());

        assertThat(actions.items.get(0).dueDate()).isNull();
    }

    @Test
    @DisplayName("담당자 미정 tuple 도 분배한다 — 거르면 검토 화면에서 통째로 사라진다")
    void 담당자_미정도_분배한다() {
        /*
         * 2026-08-07 합의로 C 가 AI 분배 경로의 담당자 미정을 허용했다
         * (ActionTypeShapePolicy.checkDistribution). 여기서 거르면 그 tuple 은 action 이 없어
         * RVW-01 조회에 안 걸리고, 담당자가 없다는 사실 자체를 사람이 볼 방법이 사라진다.
         * 확정을 막는 것은 RVW-05 의 몫이지 분배의 몫이 아니다.
         */
        FakeTupleRepository tuples = new FakeTupleRepository(
                stored(1L, tuple("로드맵 초안 작성", 42L)),
                stored(2L, tuple("빈 상태 문구 정리", null)));
        RecordingDistributionPort actions = new RecordingDistributionPort();

        int distributed = service(tuples, actions, meetingId -> Optional.of(PROJECT), false)
                .distribute(COMPANY, MEETING, Map.of());

        assertThat(distributed).isEqualTo(2);
        assertThat(actions.items).hasSize(2);
        assertThat(actions.items).extracting(ActionDistributionItem::assigneeMemberId)
                .containsExactly(42L, null);
        // 담당자가 없다고 TEAM 으로 돌리지 않는다 — 다른 종류의 액션을 지어내는 것이다.
        assertThat(actions.items).extracting(ActionDistributionItem::actionType)
                .containsOnly(ActionType.PERSONAL);
    }

    @Test
    @DisplayName("반환 순서가 요청과 어긋나면 던진다 — 엉뚱한 action_id 가 엉뚱한 tuple 에 적힌다")
    void 분배_결과_수가_다르면_던진다() {
        FakeTupleRepository tuples = new FakeTupleRepository(
                stored(1L, tuple("로드맵 초안 작성", 42L)),
                stored(2L, tuple("가격표 정리", 42L)));
        // 둘을 보냈는데 하나만 돌려주는 구현체다.
        ActionDistributionPort broken = command ->
                List.of(new DistributedAction(700L, command.items().get(0)));

        TupleDistributionService service = new TupleDistributionService(
                tuples, broken, meetingId -> Optional.of(PROJECT),
                (companyId, meetingId) -> false, new ObjectMapper());

        assertThatThrownBy(() -> service.distribute(COMPANY, MEETING, Map.of()))
                .isInstanceOf(IllegalStateException.class);
        // 되짚지 않았다 — 짝이 맞지 않는 상태로 적으면 검토 화면이 다른 배정의 근거를 보여준다.
        assertThat(tuples.applied).isEmpty();
    }

    @Test
    @DisplayName("이미 분배된 회의는 tuple 을 읽기도 전에 멈춘다 — 재분석이 액션을 두 배로 만들지 않는다")
    void 이미_분배된_회의는_멈춘다() {
        FakeTupleRepository tuples = new FakeTupleRepository(
                stored(1L, tuple("로드맵 초안 작성", 42L)));
        RecordingDistributionPort actions = new RecordingDistributionPort();

        int distributed = service(tuples, actions, meetingId -> Optional.of(PROJECT), true)
                .distribute(COMPANY, MEETING, Map.of());

        assertThat(distributed).isZero();
        assertThat(actions.items).isEmpty();
        assertThat(tuples.applied).isEmpty();
    }

    private TupleDistributionService service(FakeTupleRepository tuples,
                                            ActionDistributionPort actions,
                                            MeetingProjectProvider projects,
                                            boolean alreadyDistributed) {
        return new TupleDistributionService(tuples, actions, projects,
                (companyId, meetingId) -> alreadyDistributed, new ObjectMapper());
    }

    private static AssignmentTuple tuple(String title, Long assigneeMemberId) {
        return new AssignmentTuple(title, assigneeMemberId, AssigneeSource.EXPLICIT_CALL, null, 1L);
    }

    private static AssignmentTupleRepository.StoredTuple stored(long id, AssignmentTuple tuple) {
        return new AssignmentTupleRepository.StoredTuple(id, tuple, 1, "제품 로드맵", true);
    }

    /* 분배가 읽고 되짚는 것만 구현한 가짜. 나머지는 이 테스트가 부르지 않는다. */
    private static final class FakeTupleRepository implements AssignmentTupleRepository {

        private final List<StoredTuple> stored;
        private final Map<Long, Long> applied = new LinkedHashMap<>();

        private FakeTupleRepository(StoredTuple... rows) {
            this.stored = List.of(rows);
        }

        @Override
        public List<StoredTuple> findByMeeting(long companyId, long meetingId) {
            return stored;
        }

        @Override
        public int applyDistribution(long meetingId, List<TupleDistribution> distributions) {
            distributions.forEach(row -> applied.put(row.tupleId(), row.actionId()));
            return distributions.size();
        }

        @Override
        public void replace(long companyId, long meetingId, List<TupleRow> rows) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int applyVerifications(long meetingId, List<TupleVerification> verifications) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int applyConflicts(long meetingId, List<TupleConflicts> conflicts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int applyGateVerdicts(long meetingId, List<TupleGateVerdict> verdicts) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingDistributionPort implements ActionDistributionPort {

        private final List<ActionDistributionItem> items = new ArrayList<>();
        private long nextActionId = 700L;

        @Override
        public List<DistributedAction> distribute(DistributeActionsCommand command) {
            items.addAll(command.items());
            return command.items().stream()
                    .map(item -> new DistributedAction(nextActionId++, item))
                    .toList();
        }
    }
}
