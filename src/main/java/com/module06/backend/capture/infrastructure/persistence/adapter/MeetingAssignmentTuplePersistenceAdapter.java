package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.AssignmentTupleRepository;
import com.module06.backend.capture.domain.model.AssignmentTuple;
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
}
