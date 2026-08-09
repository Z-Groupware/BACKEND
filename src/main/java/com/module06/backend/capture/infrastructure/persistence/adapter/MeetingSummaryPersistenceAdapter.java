package com.module06.backend.capture.infrastructure.persistence.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.capture.application.port.out.MeetingSummaryRepository;
import com.module06.backend.capture.domain.model.GateVerdict;
import com.module06.backend.capture.domain.model.TopicItem;
import com.module06.backend.capture.infrastructure.persistence.entity.MeetingDecisionJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.entity.MeetingSummaryJpaEntity;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataMeetingDecisionRepository;
import com.module06.backend.capture.infrastructure.persistence.repository.SpringDataMeetingSummaryRepository;

/*
 * meeting_summary · meeting_decision 저장·조회 어댑터다.
 *
 * <h2>저장 순서가 강제되어 있다</h2>
 * meeting_decision 이 (meeting_summary_id, meeting_id) 복합 FK 로 meeting_summary 를 참조한다.
 * 그래서 요약을 **먼저 저장해 id 를 받은 뒤** 결정을 넣어야 한다. 순서를 바꾸면 FK 위반으로 막힌다.
 *
 * 이 제약이 지키는 것은 "다른 회의의 요약을 가리키면서 meeting_id 는 딴 값인 행"이다.
 * 그런 행이 생기면 회의 상세 화면이 남의 회의 결정사항을 자기 것으로 보여주는데,
 * 조회는 성공하므로 아무도 오류를 못 본다(V5.8 주석).
 */
@Repository
@RequiredArgsConstructor
public class MeetingSummaryPersistenceAdapter implements MeetingSummaryRepository {

    private final SpringDataMeetingSummaryRepository summaryRepository;
    private final SpringDataMeetingDecisionRepository decisionRepository;

    @Override
    @Transactional
    public void replace(long companyId, long meetingId, String overview, List<TopicDecisions> topics,
                        String modelName, String promptVersion) {

        MeetingSummaryJpaEntity summary = summaryRepository.findByMeetingId(meetingId)
                .map(existing -> {
                    existing.overwrite(overview, modelName, promptVersion);
                    return existing;
                })
                .orElseGet(() -> MeetingSummaryJpaEntity.of(
                        companyId, meetingId, overview, modelName, promptVersion));

        // 자식을 먼저 지운다 — FK 가 걸려 있어 부모가 남아 있는 상태에서만 안전하게 지울 수 있다.
        decisionRepository.deleteByMeetingId(meetingId);
        MeetingSummaryJpaEntity saved = summaryRepository.saveAndFlush(summary);

        List<MeetingDecisionJpaEntity> rows = new ArrayList<>();
        for (TopicDecisions topic : topics) {
            int sortOrder = 0;
            for (TopicItem item : topic.items()) {
                rows.add(MeetingDecisionJpaEntity.of(
                        saved.getId(), meetingId, topic.topicSeq(), topic.topic(),
                        item.itemType(), item.content(), item.reason(),
                        item.evidenceUtteranceId(), sortOrder++));
            }
        }
        decisionRepository.saveAll(rows);
    }

    /*
     * L3.5 판정을 반영한다. 조회한 엔티티를 고치고 트랜잭션 종료 시 더티 체킹으로 반영한다 —
     * 벌크 UPDATE 로 하면 신규 @Query 가 필요하고(QUERY_002 금지), 판정 건수를 세려면
     * 어차피 항목을 읽어야 한다.
     */
    @Override
    @Transactional
    public int applyGateVerdicts(long meetingId, List<GateVerdict> verdicts) {
        Map<Long, GateVerdict> byDecisionId = new LinkedHashMap<>();
        for (GateVerdict verdict : verdicts) {
            if (verdict.decisionId() != null && verdict.gateStatus() != null) {
                byDecisionId.put(verdict.decisionId(), verdict);
            }
        }
        if (byDecisionId.isEmpty()) {
            return 0;
        }

        // meetingId 를 조건에 함께 넣는다 — 계층 응답이 실어 온 id 만으로 갱신하면 다른 회의의
        // 항목을 고칠 수 있다. 조회 결과에 없는 id 는 그냥 반영되지 않는다(미판정으로 남는다).
        List<MeetingDecisionJpaEntity> rows =
                decisionRepository.findByMeetingIdAndIdIn(meetingId, byDecisionId.keySet());

        int applied = 0;
        for (MeetingDecisionJpaEntity row : rows) {
            GateVerdict verdict = byDecisionId.get(row.getId());
            if (verdict != null) {
                row.applyGate(verdict.gateStatus());
                applied++;
            }
        }
        return applied;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MeetingSummaryView> findByMeeting(long companyId, long meetingId) {
        // 회사 스코프를 조건에 넣는다. 아래 topicsOf(meetingId) 는 meetingId 로만 찾지만,
        // meeting_decision 이 (meeting_summary_id, meeting_id) 복합 FK 라 이 요약에 속한
        // 항목만 존재한다 — 요약이 이 회사 것임을 위에서 확인했으므로 범위 안이다.
        return summaryRepository.findByMeetingIdAndCompanyId(meetingId, companyId)
                .map(summary -> new MeetingSummaryView(summary.getOverview(), topicsOf(meetingId)));
    }

    /*
     * 주제별로 묶는다. 정렬은 쿼리가 이미 (topic_seq, sort_order) 로 해두므로 LinkedHashMap 이
     * 그 순서를 유지한다 — 여기서 다시 정렬하면 계층이 정한 순서와 갈릴 여지가 생긴다.
     */
    private List<TopicView> topicsOf(long meetingId) {
        Map<Integer, TopicView> grouped = new LinkedHashMap<>();

        for (MeetingDecisionJpaEntity row : decisionRepository
                .findByMeetingIdOrderByTopicSeqAscSortOrderAsc(meetingId)) {

            TopicView topic = grouped.computeIfAbsent(row.getTopicSeq(),
                    seq -> new TopicView(seq, row.getTopic(), new ArrayList<>()));

            topic.items().add(new ItemView(
                    row.getId(), row.getItemType(), row.getContent(), row.getReason(),
                    row.getEvidenceTranscriptId(), row.getGateStatus()));
        }
        return List.copyOf(grouped.values());
    }
}
