package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.AssignmentTupleRepository;
import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.GateSignals;
import com.module06.backend.capture.infrastructure.persistence.entity.MeetingAssignmentTupleJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataMeetingAssignmentTupleRepository;

/*
 * meeting_assignment_tuple 저장 어댑터다.
 *
 * 삭제 후 삽입을 한 트랜잭션에 묶는다. 나누면 지우고 나서 삽입이 실패했을 때 tuple 이 통째로
 * 사라진 회의가 남는데, 그 상태는 "분석은 완료인데 배정이 없는" 것으로 보여 L4 가 아무것도
 * 못 뽑은 것과 구분되지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class MeetingAssignmentTuplePersistenceAdapter implements AssignmentTupleRepository {

    private final SpringDataMeetingAssignmentTupleRepository tupleRepository;

    @Override
    @Transactional
    public void replace(long companyId, long meetingId, List<TupleRow> rows) {
        tupleRepository.deleteByMeetingId(meetingId);

        List<MeetingAssignmentTupleJpaEntity> entities = new ArrayList<>();
        int sortOrder = 0;
        for (TupleRow row : rows) {
            AssignmentTuple tuple = row.tuple();
            entities.add(MeetingAssignmentTupleJpaEntity.of(
                    companyId, meetingId, row.decisionId(), row.topicSeq(), row.topic(),
                    tuple.title(), tuple.assigneeCandidateMemberId(), tuple.assigneeSource(),
                    tuple.dueDate(), tuple.evidenceUtteranceId(),
                    row.modelName(), row.promptVersion(), sortOrder++));
        }
        tupleRepository.saveAll(entities);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredTuple> findByMeeting(long companyId, long meetingId) {
        return tupleRepository.findByCompanyIdAndMeetingIdOrderBySortOrderAsc(companyId, meetingId).stream()
                .map(entity -> new StoredTuple(
                        entity.getId(),
                        new AssignmentTuple(
                                entity.getTitle(),
                                entity.getAssigneeCandidateMemberId(),
                                entity.getAssigneeSource(),
                                entity.getDueDate(),
                                entity.getEvidenceTranscriptId()),
                        entity.getTopicSeq(),
                        entity.getTopic(),
                        // L7 게이트의 네 번째 조건. NULL(L5 미수행)을 그대로 넘긴다 —
                        // 여기서 false 로 바꾸면 "검증에서 걸림"과 "검증 안 함"이 뭉친다.
                        entity.getVerifyAgree()))
                .toList();
    }

    /*
     * L5 판정을 반영한다. applyGateVerdicts 와 같은 모양이다 — 엔티티를 조회해 고치고
     * 더티 체킹으로 반영한다. 벌크 UPDATE 로 하면 신규 @Query 가 필요하고(QUERY_002),
     * 반영 건수를 세려면 어차피 행을 읽어야 한다.
     */
    @Override
    @Transactional
    public int applyVerifications(long meetingId, List<TupleVerification> verifications) {
        Map<Long, TupleVerification> byTupleId = new LinkedHashMap<>();
        for (TupleVerification verification : verifications) {
            if (verification.tupleId() != null) {
                byTupleId.put(verification.tupleId(), verification);
            }
        }
        if (byTupleId.isEmpty()) {
            return 0;
        }

        // meetingId 를 조건에 함께 넣는다 — id 만으로 갱신하면 다른 회의의 행을 고칠 수 있다.
        // 조회 결과에 없는 id 는 반영되지 않고 미검증(NULL)으로 남는다.
        List<MeetingAssignmentTupleJpaEntity> rows =
                tupleRepository.findByMeetingIdAndIdIn(meetingId, byTupleId.keySet());

        int applied = 0;
        for (MeetingAssignmentTupleJpaEntity row : rows) {
            TupleVerification verification = byTupleId.get(row.getId());
            if (verification != null) {
                row.applyVerification(
                        verification.agree(), verification.disagreementFields(), verification.verdict(),
                        verification.reason(), verification.modelName(), verification.promptVersion());
                applied++;
            }
        }
        return applied;
    }

    /*
     * L6 결과를 반영한다. **모순이 없는 행도 반영한다** — 검사 시각이 찍혀야
     * "검사했고 깨끗함"이 되고, 안 찍으면 "아직 안 봄"으로 남는다(V5.14 주석).
     */
    @Override
    @Transactional
    public int applyConflicts(long meetingId, List<TupleConflicts> conflicts) {
        Map<Long, TupleConflicts> byTupleId = new LinkedHashMap<>();
        for (TupleConflicts conflict : conflicts) {
            if (conflict.tupleId() != null) {
                byTupleId.put(conflict.tupleId(), conflict);
            }
        }
        if (byTupleId.isEmpty()) {
            return 0;
        }

        List<MeetingAssignmentTupleJpaEntity> rows =
                tupleRepository.findByMeetingIdAndIdIn(meetingId, byTupleId.keySet());

        int applied = 0;
        for (MeetingAssignmentTupleJpaEntity row : rows) {
            TupleConflicts conflict = byTupleId.get(row.getId());
            if (conflict != null) {
                row.applyConflicts(conflict.conflicts().stream().map(Enum::name).toList());
                applied++;
            }
        }
        return applied;
    }

    @Override
    @Transactional
    public int applyGateVerdicts(long meetingId, List<TupleGateVerdict> verdicts) {
        Map<Long, TupleGateVerdict> byTupleId = new LinkedHashMap<>();
        for (TupleGateVerdict verdict : verdicts) {
            if (verdict.tupleId() != null) {
                byTupleId.put(verdict.tupleId(), verdict);
            }
        }
        if (byTupleId.isEmpty()) {
            return 0;
        }

        List<MeetingAssignmentTupleJpaEntity> rows =
                tupleRepository.findByMeetingIdAndIdIn(meetingId, byTupleId.keySet());

        int applied = 0;
        for (MeetingAssignmentTupleJpaEntity row : rows) {
            TupleGateVerdict verdict = byTupleId.get(row.getId());
            if (verdict != null) {
                GateSignals signals = verdict.signals();
                row.applyGate(verdict.autoConfirmed(), signals.hasEvidence(), signals.assigneeInRoster(),
                        signals.assigneeSourceOk(), signals.viewsAgree());
                applied++;
            }
        }
        return applied;
    }
}
