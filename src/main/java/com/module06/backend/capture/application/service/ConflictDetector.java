package com.module06.backend.capture.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
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
import com.module06.backend.capture.domain.model.Utterance;

/*
 * L6 규칙·모순 검사의 판정 로직이다. **모델이 아니라 코드다**(명세 「L6 규칙·모순 검사 — Spring」).
 *
 * <h2>무엇을 검사하나</h2>
 * 데이터만 보고 **확실히 말할 수 있는 모순**만 본다. "이 배정이 적절한가" 같은 판단은
 * 하지 않는다 — 그건 L5 가 관점을 바꿔 묻는 일이고, 코드가 흉내내면 규칙이 계속 자라면서
 * 아무도 전체를 이해하지 못하는 덩어리가 된다.
 *
 * <h2>왜 Spring 의존이 없나</h2>
 * L1 의 SpeakerAttributionResolver 와 같은 이유다. 판정이 순수 계산이라 저장소도 HTTP 도
 * 필요 없고, 그래서 경계 입력(회의 날짜 없음 · 화자 미상 · 오버랩 중복)을 전부 단위
 * 테스트로 고정할 수 있다. 이 판정이 헐거우면 검증되지 않은 배정이 자동확정으로 나간다.
 *
 * <h2>모순은 tuple 을 지우지 않는다</h2>
 * 표시만 하고 남긴다. 지우면 사람이 검토할 대상 자체가 사라진다. 대신 L7 이 모순 있는
 * tuple 을 자동확정하지 않는다 — 게이트는 조이는 방향으로만 쓴다.
 */
@Slf4j
@Service
public class ConflictDetector {

    /*
     * 저장된 tuple 전부를 보고 각각의 모순을 찾는다.
     *
     * **회의 단위로 한 번에 본다.** 중복 검사(DUPLICATE_EVIDENCE)가 tuple 하나만 보고는
     * 성립하지 않기 때문이다 — 같은 근거에서 나온 다른 tuple 이 있는지는 전체를 봐야 안다.
     *
     * @param stored        그 회의에 저장된 tuple 전부
     * @param utterances    판정에 쓰인 발화. 근거 발화의 화자를 되짚는 데 쓴다
     * @param attendeeMemberIds 참석자 명단(명단 밖 탈출구 제외)
     * @param meetingDate   회의 날짜. null 이면 기한 검사를 건너뛴다
     * @return tupleId → 모순 목록. 모순이 없는 tuple 은 빈 목록으로 들어간다 —
     *         "검사했고 깨끗함"과 "검사 안 함"을 호출자가 구분할 수 있어야 한다
     */
    public Map<Long, List<ConflictType>> detect(List<StoredTuple> stored, List<Utterance> utterances,
                                                Set<Long> attendeeMemberIds, LocalDate meetingDate) {

        Set<Long> duplicatedEvidence = duplicatedEvidence(stored);
        Map<Long, Long> speakerByUtteranceId = speakerByUtteranceId(utterances);

        Map<Long, List<ConflictType>> byTupleId = new HashMap<>();
        for (StoredTuple row : stored) {
            byTupleId.put(row.id(), detectOne(row, duplicatedEvidence, speakerByUtteranceId,
                    attendeeMemberIds, meetingDate));
        }

        long flagged = byTupleId.values().stream().filter(conflicts -> !conflicts.isEmpty()).count();
        log.info("L6 모순 검사 — tuple {}건 중 {}건에서 모순 검출", stored.size(), flagged);
        return byTupleId;
    }

    private List<ConflictType> detectOne(StoredTuple row, Set<Long> duplicatedEvidence,
                                         Map<Long, Long> speakerByUtteranceId,
                                         Set<Long> attendeeMemberIds, LocalDate meetingDate) {
        AssignmentTuple tuple = row.tuple();
        List<ConflictType> conflicts = new ArrayList<>();

        if (tuple.evidenceUtteranceId() != null && duplicatedEvidence.contains(tuple.evidenceUtteranceId())) {
            conflicts.add(ConflictType.DUPLICATE_EVIDENCE);
        }

        /*
         * 기한이 회의보다 과거다. 회의 날짜를 모르면 검사하지 않는다 — 오늘 날짜로 대신 재면
         * 몇 주 뒤에 재실행했을 때 멀쩡한 기한이 전부 과거로 잡힌다.
         *
         * 같은 날(회의 당일 마감)은 모순이 아니다. "오늘 안에 해주세요"는 흔한 말이다.
         */
        if (meetingDate != null && tuple.dueDate() != null && tuple.dueDate().isBefore(meetingDate)) {
            conflicts.add(ConflictType.DUE_BEFORE_MEETING);
        }

        if (tuple.assigneeCandidateMemberId() != null
                && !attendeeMemberIds.contains(tuple.assigneeCandidateMemberId())) {
            conflicts.add(ConflictType.ASSIGNEE_NOT_IN_ROSTER);
        }

        /*
         * 1인칭인데 근거 발화의 화자를 모른다 — "제가 할게요"의 '제가'가 누군지 모르는 것이라
         * 담당자 근거가 성립하지 않는다. 그런데도 담당자가 채워져 있으면 그 값은 다른 데서 온
         * 추론이다.
         *
         * 담당자가 없으면 검사하지 않는다. 그때는 배정 자체가 없어 근거를 따질 대상이 없다.
         */
        if (tuple.assigneeSource() == AssigneeSource.FIRST_PERSON
                && tuple.assigneeCandidateMemberId() != null
                && speakerByUtteranceId.get(tuple.evidenceUtteranceId()) == null) {
            conflicts.add(ConflictType.FIRST_PERSON_WITHOUT_SPEAKER);
        }

        if (tuple.assigneeSource() != null && tuple.assigneeCandidateMemberId() == null) {
            conflicts.add(ConflictType.SOURCE_WITHOUT_ASSIGNEE);
        }

        return conflicts;
    }

    /*
     * 둘 이상의 tuple 이 같은 근거 발화를 쓰고 있는가.
     *
     * L2 가 주제 경계에 오버랩 3발화를 얹어 돌려주므로(경계 발화를 L3 가 양쪽에서 봐야 한다),
     * 오버랩 구간의 발화 하나가 두 주제에서 각각 항목이 되고 각각 tuple 이 될 수 있다.
     * 드문 사고가 아니라 정상 동작의 부산물이다.
     *
     * **양쪽 다 표시한다.** 둘 중 어느 쪽이 원본인지 코드가 알 수 없다 — 하나를 골라
     * 남기면 그 선택이 곧 추측이고, 사람이 검토할 때 지워진 쪽을 볼 방법이 없다.
     */
    private Set<Long> duplicatedEvidence(List<StoredTuple> stored) {
        Set<Long> seen = new HashSet<>();
        Set<Long> duplicated = new HashSet<>();
        for (StoredTuple row : stored) {
            Long evidence = row.tuple().evidenceUtteranceId();
            if (evidence != null && !seen.add(evidence)) {
                duplicated.add(evidence);
            }
        }
        return duplicated;
    }

    /*
     * 발화 id → 화자. 화자가 미정(null)인 발화는 **키 자체를 넣지 않는다** — 그래야
     * get 결과 null 이 "화자 미정"과 "그런 발화 없음"을 함께 뜻하게 되고, 둘 다 1인칭
     * 근거가 성립하지 않는 경우라 같은 판정으로 묶여야 맞다.
     */
    private Map<Long, Long> speakerByUtteranceId(List<Utterance> utterances) {
        Map<Long, Long> byId = new HashMap<>();
        for (Utterance utterance : utterances) {
            if (utterance.utteranceId() != null && utterance.speakerMemberId() != null) {
                byId.put(utterance.utteranceId(), utterance.speakerMemberId());
            }
        }
        return byId;
    }
}
