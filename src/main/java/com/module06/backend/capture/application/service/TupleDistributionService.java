package com.module06.backend.capture.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.module06.backend.action.application.port.ActionDistributionPort;
import com.module06.backend.action.application.port.ActionDistributionPort.ActionDistributionItem;
import com.module06.backend.action.application.port.ActionDistributionPort.DistributeActionsCommand;
import com.module06.backend.action.application.port.ActionDistributionPort.DistributedAction;
import com.module06.backend.action.domain.model.ActionType;
import com.module06.backend.capture.application.port.out.AssignmentTupleRepository;
import com.module06.backend.capture.application.port.out.AssignmentTupleRepository.StoredTuple;
import com.module06.backend.capture.application.port.out.AssignmentTupleRepository.TupleDistribution;
import com.module06.backend.capture.application.port.out.DistributedActionProbe;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.GateSignals;

/*
 * tuple 을 action 으로 분배한다. 파이프라인(L1~L7)의 산출물이 **처음 A 밖으로 나가는 자리**다.
 *
 * <h2>분석 직후에 분배한다</h2>
 * 08/05 확정(ActionDistributionPort 주석) — "A 가 회의 분석 직후 액션을 일괄 생성한다, 보드
 * 노출 여부는 생성 시점이 아니라 review_status 로 걸러진다". 그래서 여기서 만들어지는 액션은
 * 전부 PENDING 이고(V2.6.2 기본값), 검토 화면(RVW-01)이 그것을 읽는다. 사람의 확인을 받아
 * 확정·분배하는 것은 RVW-05 다.
 *
 * <h2>두 묶음을 모두 분배한다</h2>
 * 게이트를 통과한 것만 만들면 「AI 확인 필요」 묶음이 검토 화면에 아예 나타나지 않는다 —
 * 사람이 봐야 하는 쪽이 그쪽인데 볼 방법이 없어진다. 자동확정 여부는 액션을 만들지 말지가
 * 아니라 화면에서 어느 묶음에 놓을지를 정한다.
 *
 * <h2>한 트랜잭션이다</h2>
 * action 생성과 action_id 되짚기가 갈리면, 액션은 만들어졌는데 tuple 이 그것을 모르는 상태가
 * 남는다. 다음 실행이 같은 배정을 다시 분배해 **같은 일이 사람 보드에 두 번 꽂힌다.**
 * ActionDistributionService 는 REQUIRES_NEW 없이 기본 전파라 이 트랜잭션에 참여한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TupleDistributionService {

    /* action.title 은 VARCHAR(200) 이고 tuple.title 은 300 이다. 넘치면 INSERT 가 깨진다. */
    private static final int ACTION_TITLE_MAX = 200;

    private final AssignmentTupleRepository assignmentTupleRepository;
    private final ActionDistributionPort actionDistributionPort;
    private final MeetingProjectProvider meetingProjectProvider;
    private final DistributedActionProbe distributedActionProbe;
    private final ObjectMapper objectMapper;

    /*
     * 회의의 tuple 을 액션으로 만든다.
     *
     * @param gateVerdicts L7 판정. tuple id → 판정. 없는 tuple 은 게이트 신호 없이 분배된다 —
     *                     신호를 지어내지 않는다.
     * @return 만들어진 액션 수
     */
    @Transactional
    public int distribute(long companyId, long meetingId, Map<Long, AutoConfirmGate.Verdict> gateVerdicts) {
        /*
         * 이미 분배된 회의는 다시 분배하지 않는다.
         *
         * ANLZ-01 강제 재실행은 tuple 을 통째로 갈아끼우므로(replace) 이전 실행이 적어 둔
         * action_id 가 사라진다. tuple 만 보면 "아직 분배 안 됨"으로 보이는데, action 쪽에는
         * 이전 벌이 그대로 있다. 그걸 모르고 만들면 같은 일이 두 벌로 남는다.
         *
         * 여기서 이전 액션을 지우지 않는 이유 — action 은 C 도메인 소유이고 회수 경로가 아직
         * 없다. 지우는 쪽이 아니라 **멈추는 쪽**이 안전하다. 재분석 결과를 반영해야 하면
         * 회수 계약이 먼저 필요하다.
         */
        if (distributedActionProbe.existsForMeeting(companyId, meetingId)) {
            log.warn("분배 생략 — 이 회의에는 이미 분석 경로로 만든 액션이 있다. "
                    + "재분석 결과는 액션에 반영되지 않는다. meetingId={}", meetingId);
            return 0;
        }

        List<StoredTuple> stored = assignmentTupleRepository.findByMeeting(companyId, meetingId);
        if (stored.isEmpty()) {
            log.info("분배 생략 — 분배할 tuple 이 없다. meetingId={}", meetingId);
            return 0;
        }

        /*
         * 프로젝트를 못 읽으면 분배하지 않는다. action.project_id 는 NOT NULL 이고, 임의의
         * 값으로 채우면 그 액션이 엉뚱한 프로젝트 보드에 꽂힌다. 마감일 기본값도 프로젝트에서
         * 계산되므로 기한까지 함께 틀린다.
         */
        Long projectId = meetingProjectProvider.projectIdOf(meetingId).orElse(null);
        if (projectId == null) {
            log.warn("분배 생략 — 회의의 프로젝트를 읽지 못했다. meetingId={}", meetingId);
            return 0;
        }

        List<StoredTuple> distributable = new ArrayList<>();
        List<ActionDistributionItem> items = new ArrayList<>();
        for (StoredTuple tuple : stored) {
            /*
             * 담당자가 없는 tuple 은 분배할 수 없다.
             *
             * 분석 경로는 PERSONAL 액션만 생산하고(C 와 협의 · 2026-08-06), PERSONAL 은
             * 담당자가 필수다(ActionTypeShapePolicy). 담당자 미정을 TEAM 으로 돌리는 것은
             * 다른 종류의 액션을 지어내는 것이라 하지 않는다.
             *
             * ⚠ 그래서 **담당자 미정 tuple 은 검토 화면(RVW-01)에 나타나지 않는다.** 대기실에
             * 남아 있고 action 이 없으니 조회에 걸리지 않는다. 담당자가 없다는 것 자체가 사람이
             * 봐야 하는 상태인데 볼 방법이 없으므로, 계약 보강이 필요한 지점이다.
             */
            if (tuple.tuple().assigneeCandidateMemberId() == null) {
                continue;
            }
            distributable.add(tuple);
            items.add(itemOf(tuple, companyId, meetingId, projectId, gateVerdicts.get(tuple.id())));
        }

        int skipped = stored.size() - distributable.size();
        if (skipped > 0) {
            log.warn("담당자 미정 tuple 은 분배하지 않았다 — 검토 화면에도 나타나지 않는다. "
                    + "meetingId={} 미분배={}/{}", meetingId, skipped, stored.size());
        }
        if (items.isEmpty()) {
            return 0;
        }

        List<DistributedAction> distributed = actionDistributionPort.distribute(
                new DistributeActionsCommand(items));
        /*
         * 입력 순서 = 반환 순서라는 계약(ActionDistributionPort.distribute)에 기대어 짝짓는다.
         * 어긋나면 엉뚱한 action_id 가 엉뚱한 tuple 에 적히는데, 그 상태는 검토 화면이 다른
         * 배정의 근거를 보여주는 것이므로 여기서 막는다.
         */
        if (distributed.size() != items.size()) {
            throw new IllegalStateException(
                    "분배된 액션 수가 요청과 다릅니다: 요청=" + items.size() + ", 분배=" + distributed.size());
        }

        List<TupleDistribution> backfill = new ArrayList<>();
        for (int i = 0; i < distributed.size(); i++) {
            backfill.add(new TupleDistribution(distributable.get(i).id(), distributed.get(i).actionId()));
        }
        int applied = assignmentTupleRepository.applyDistribution(meetingId, backfill);
        if (applied != backfill.size()) {
            // 되짚지 못한 행이 있다. 오류로 올리지 않는다 — 액션은 이미 만들어졌고, 여기서
            // 던지면 트랜잭션이 함께 되감겨 방금 만든 액션까지 사라진다.
            log.warn("분배 결과 반영 수가 대상과 다르다 — meetingId={} 대상={} 반영={}",
                    meetingId, backfill.size(), applied);
        }

        log.info("분배 완료 — meetingId={} 액션 {}건 (tuple {}건 중)",
                meetingId, distributed.size(), stored.size());
        return distributed.size();
    }

    private ActionDistributionItem itemOf(StoredTuple stored, long companyId, long meetingId,
                                          Long projectId, AutoConfirmGate.Verdict verdict) {
        AssignmentTuple tuple = stored.tuple();
        return new ActionDistributionItem(
                clip(tuple.title()),
                /*
                 * 설명을 비워 보낸다. tuple 에는 제목만 있고, 주제나 근거 발화를 설명에 옮겨
                 * 적으면 "AI 가 쓴 설명"과 "회의에서 나온 말"이 구분되지 않는다. 근거는
                 * evidenceTranscriptId 로 이어져 있고 검토 화면이 그것을 인용한다.
                 */
                null,
                // 분석 경로는 PERSONAL 만 생산한다(C 와 협의 · 2026-08-06). L4 가 모델에 주는
                // 닫힌 목록이 참석자 명단(사람)이라 구조적으로 팀이 나올 수 없다.
                ActionType.PERSONAL,
                tuple.assigneeCandidateMemberId(),
                // null 이면 C 가 프로젝트 마감일로 채우고 due_date_defaulted 로 표시한다 —
                // 여기서 오늘 날짜 같은 값으로 채우면 그럴듯하게 틀린 마감이 보드에 꽂힌다.
                tuple.dueDate(),
                meetingId,
                projectId,
                companyId,
                toActionAssigneeSource(tuple.assigneeSource()),
                tuple.evidenceUtteranceId(),
                gateSignalsJson(verdict),
                // 분석 경로다. 사람이 "+"로 추가한 액션(RVW-03)만 true 다.
                false);
    }

    /*
     * 게이트 판정을 JSON 으로 싣는다(action.gate_signals).
     *
     * A 쪽 원본은 meeting_assignment_tuple 의 컬럼 넷이고 이 값은 C 가 갖는 사본이다.
     * autoConfirmed 를 함께 넣는 이유 — 신호 넷만 남기면 "넷은 통과했는데 L6 모순 때문에 걸린
     * 건"이 사본에서 통과한 것처럼 보인다.
     *
     * L7 판정이 없으면 null 이다. 빈 객체나 false 넷으로 채우면 "게이트가 떨어뜨렸다"로
     * 읽히는데, 그건 "게이트를 지나지 않았다"와 다른 상태다.
     */
    private String gateSignalsJson(AutoConfirmGate.Verdict verdict) {
        if (verdict == null || verdict.signals() == null) {
            return null;
        }
        GateSignals signals = verdict.signals();
        // 순서를 명세(RVW-01 gate.signals)와 같게 두려고 LinkedHashMap 을 쓴다. Map.of 는
        // 순서를 보장하지 않아 같은 판정이 실행마다 다른 순서로 저장된다.
        Map<String, Boolean> payload = new LinkedHashMap<>();
        payload.put("hasEvidence", signals.hasEvidence());
        payload.put("assigneeInRoster", signals.assigneeInRoster());
        payload.put("assigneeSourceOk", signals.assigneeSourceOk());
        payload.put("viewsAgree", signals.viewsAgree());
        payload.put("autoConfirmed", verdict.autoConfirmed());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            // boolean 다섯 개를 직렬화하다 실패할 일은 없다. 그래도 분배 전체를 세우지는
            // 않는다 — 사본을 못 만든 것이고, 원본(tuple 컬럼)은 이미 저장돼 있다.
            log.warn("게이트 신호 직렬화에 실패해 사본 없이 분배한다.", e);
            return null;
        }
    }

    /*
     * 같은 이름의 enum 이 두 도메인에 따로 있다(capture · action). 값이 같아도 타입이 달라
     * 여기서 옮긴다 — 한쪽이 다른 쪽 enum 을 그대로 쓰면 그 지점에서 도메인 경계가 사라진다.
     *
     * 이름으로 옮기고 모르는 값은 예외로 드러낸다(valueOf). 판정 근거를 null 로 눌러 담으면
     * 게이트 조건3(명시적 호명 / 1인칭)이 사본에서 사라진다.
     */
    private com.module06.backend.action.domain.model.AssigneeSource toActionAssigneeSource(
            com.module06.backend.capture.domain.model.AssigneeSource source) {
        if (source == null) {
            return null;
        }
        return com.module06.backend.action.domain.model.AssigneeSource.valueOf(source.name());
    }

    /* 넘치는 제목은 자른다. 던지면 배정 하나의 길이 때문에 회의 전체가 분배되지 않는다. */
    private String clip(String title) {
        if (title == null || title.length() <= ACTION_TITLE_MAX) {
            return title;
        }
        return title.substring(0, ACTION_TITLE_MAX);
    }
}
