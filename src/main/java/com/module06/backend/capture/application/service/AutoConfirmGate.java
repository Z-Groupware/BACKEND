package com.module06.backend.capture.application.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import com.module06.backend.capture.application.port.out.AssignmentTupleRepository.StoredTuple;
import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.ConflictType;
import com.module06.backend.capture.domain.model.GateSignals;
import com.module06.backend.capture.domain.model.Utterance;

/*
 * L7 자동확정 게이트다. **모델이 아니라 코드가 판정한다**(명세 「자동 확정 게이트 (4조건) — Spring」).
 *
 * 이 게이트가 가른 두 묶음이 곧 검토 화면의 「AI 확신도 높음」과 「AI 확인 필요」다.
 *
 * <h2>모델의 확신도를 쓰지 않는다</h2>
 * 자기보고 신뢰도는 실제 정확도와 맞지 않는다 — LLM 에 물으면 85~95 에 몰리고 틀린 답에도
 * 높은 숫자를 붙인다. 대신 코드로 확인할 수 있는 사실 넷만 본다(GateSignals).
 *
 * <h2>자동확정은 "분배됐다"가 아니다</h2>
 * 자동 확정 건도 **분배 전까지는 아무 데도 가 있지 않다**(명세 RVW-01). 실제 분배는
 * RVW-05 가 사람의 확인을 받아 하고, 그때 action_id 가 채워진다. 이 계층은 검토 화면의
 * 묶음을 가르는 것까지다 — 그래서 여기서 action 을 만들지 않는다.
 *
 * <h2>게이트는 조이는 방향으로만 더한다</h2>
 * 명세의 4조건에 더해 **L6 모순이 없을 것**과 **담당자를 코드가 잇지 않았을 것**을 요구한다.
 * 조건을 더하는 것은 자동확정을 줄이는 방향이라 "넷을 전부 만족할 때만"이라는 규칙과
 * 충돌하지 않는다. 반대로 조건을 빼는 변경은 검증되지 않은 배정을 사람 눈 밖으로 내보내므로
 * 함부로 하지 않는다.
 */
@Slf4j
@Service
public class AutoConfirmGate {

    /*
     * tuple 마다 게이트를 통과시킬지 판정한다.
     *
     * @param stored         그 회의에 저장된 tuple 전부
     * @param conflictsByTupleId L6 결과. 모순이 있으면 신호와 무관하게 자동확정하지 않는다
     * @param utterances     판정에 쓰인 발화. 1인칭 근거의 화자 확정 여부를 되짚는다
     * @param attendeeMemberIds 참석자 명단(명단 밖 탈출구 제외)
     * @return tupleId → 판정. 신호 넷을 그대로 담아 돌려준다 — 어느 조건에서 걸렸는지
     *         남지 않으면 게이트를 조일지 풀지 판단할 수 없다
     */
    public Map<Long, Verdict> evaluate(List<StoredTuple> stored,
                                       Map<Long, List<ConflictType>> conflictsByTupleId,
                                       List<Utterance> utterances,
                                       Set<Long> attendeeMemberIds) {

        Set<Long> attributedUtteranceIds = attributedUtteranceIds(utterances);

        Map<Long, Verdict> byTupleId = new HashMap<>();
        for (StoredTuple row : stored) {
            GateSignals signals = signalsOf(row, attendeeMemberIds, attributedUtteranceIds);
            List<ConflictType> conflicts = conflictsByTupleId.getOrDefault(row.id(), List.of());
            byTupleId.put(row.id(), new Verdict(signals,
                    signals.allPassed() && conflicts.isEmpty() && !nearMatched(row)));
        }

        long confirmed = byTupleId.values().stream().filter(Verdict::autoConfirmed).count();
        log.info("L7 자동확정 게이트 — tuple {}건 중 {}건 자동확정, {}건 검토 필요",
                stored.size(), confirmed, stored.size() - confirmed);
        return byTupleId;
    }

    /*
     * 담당자를 **코드가 이었는가**(V5.22 · NearNameAssigneeResolver).
     *
     * L6 모순과 같은 자리에 둔다 — GateSignals 안이 아니라 밖이다. 신호 넷은 명세가 정한
     * 4조건이고, 이건 우리가 더한 조건이다. 섞으면 "명세의 조건에서 걸린 건"과 "우리가 더한
     * 조건에서 걸린 건"이 한 값이 되어 게이트를 어디서 조였는지 되짚을 수 없다.
     *
     * 왜 빼는가 — 근접 매칭의 정확도는 회의 1 건 · 배정 4 건 규모에서만 쟀다. 그 표본으로
     * "오답 0 건"을 자동확정의 근거로 쓸 수 없다. 코드가 이은 담당자는 검토 화면의
     * 「AI 확인 필요」로 올려 사람이 확인하게 하고, 그 확인 결과가 임계값(현재 한 글자)을
     * 넓힐지 정하는 데이터가 된다.
     *
     * **NULL 은 빼지 않는다.** NULL 은 근접 매칭 이전에 저장된 행이고, 그것까지 빼면 이 코드
     * 이전 회의의 자동확정이 통째로 사라진다 — 게이트를 조이는 것이 아니라 과거를 지우는 것이다.
     */
    private boolean nearMatched(StoredTuple row) {
        return Boolean.TRUE.equals(row.assigneeNearMatched());
    }

    private GateSignals signalsOf(StoredTuple row, Set<Long> attendeeMemberIds,
                                  Set<Long> attributedUtteranceIds) {
        AssignmentTuple tuple = row.tuple();
        boolean hasEvidence = tuple.evidenceUtteranceId() != null;

        // NULL 담당자는 unknown_person(명단 밖)이거나 미정이다. 둘 다 배정 불가라 통과시키지 않는다.
        boolean assigneeInRoster = tuple.assigneeCandidateMemberId() != null
                && attendeeMemberIds.contains(tuple.assigneeCandidateMemberId());

        /*
         * 조건4 — L5 두 관점 일치. **NULL 을 통과시키지 않는다.**
         * NULL 은 L5 가 이 행을 보지 않았다는 뜻이고, 검증하지 않은 배정을 "확신도 높음"으로
         * 올리는 것은 게이트가 막으려던 상태 그대로다. TRUE 만 통과다.
         */
        boolean viewsAgree = Boolean.TRUE.equals(row.verifyAgree());

        return new GateSignals(hasEvidence, assigneeInRoster,
                assigneeSourceOk(tuple, attributedUtteranceIds), viewsAgree);
    }

    /*
     * 조건3 — "명시적 호명이거나 1인칭+화자확정"(명세 RVW-01 처리 정책).
     *
     * 두 경로의 신뢰도가 다르다. 이름을 불러 지시한 것(EXPLICIT_CALL)은 발화 자체에 담당자가
     * 들어 있어 화자를 몰라도 성립한다. 반면 1인칭(FIRST_PERSON)은 **말한 사람**이 담당자라는
     * 뜻이라, 그 발화의 화자가 확정되지 않으면 '제가'가 누군지 모르는 것이고 배정 근거가
     * 성립하지 않는다.
     *
     * ⚠ CAP-11(자막 청크 전송)이 붙기 전에는 L1 이 전원 판정을 포기하므로 1인칭 경로가
     * 전부 여기서 걸린다. 그건 정상이다 — 근거를 확인할 수 없는 배정을 자동확정하지 않는
     * 것이 이 조건의 목적이다.
     *
     * assigneeSource 가 null 이면 판정 불가이므로 통과시키지 않는다.
     */
    private boolean assigneeSourceOk(AssignmentTuple tuple, Set<Long> attributedUtteranceIds) {
        if (tuple.assigneeSource() == AssigneeSource.EXPLICIT_CALL) {
            return true;
        }
        if (tuple.assigneeSource() == AssigneeSource.FIRST_PERSON) {
            return tuple.evidenceUtteranceId() != null
                    && attributedUtteranceIds.contains(tuple.evidenceUtteranceId());
        }
        return false;
    }

    /* 화자가 확정된 발화의 id. L1 이 판정을 포기한 발화는 들어 있지 않다. */
    private Set<Long> attributedUtteranceIds(List<Utterance> utterances) {
        Set<Long> ids = new HashSet<>();
        for (Utterance utterance : utterances) {
            if (utterance.utteranceId() != null && utterance.speakerMemberId() != null) {
                ids.add(utterance.utteranceId());
            }
        }
        return ids;
    }

    /*
     * tuple 하나의 게이트 판정.
     *
     * signals 를 함께 들고 다니는 이유 — autoConfirmed 만 남기면 "왜 걸렸는지"가 사라진다.
     * 자동확정이 틀렸을 때 어느 조건이 헐거웠는지 알아야 게이트를 조일 수 있고, 그 숫자가
     * QLTY-02 의 autoConfirmErrorRate 를 해석하는 재료다.
     */
    public record Verdict(GateSignals signals, boolean autoConfirmed) {
    }
}
