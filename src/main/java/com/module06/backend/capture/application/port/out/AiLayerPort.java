package com.module06.backend.capture.application.port.out;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.capture.domain.model.AssignmentTuple;
import com.module06.backend.capture.domain.model.GateStatus;
import com.module06.backend.capture.domain.model.ItemType;
import com.module06.backend.capture.domain.model.Utterance;

/*
 * AI EC2(Python) 내부 API를 부르는 아웃바운드 포트다.
 *
 * 포트로 두는 이유가 두 가지다.
 *  ① 오케스트레이션 테스트가 실제 HTTP 없이 돈다. 계층 순서·잠금·상태 전이는 이 저장소의 로직이고,
 *     그걸 검증하는 데 Gemini 크레딧이나 네트워크가 필요하면 아무도 테스트를 돌리지 않게 된다.
 *  ② 계층을 특화 모델로 갈아끼우거나 제공자를 바꿔도 이 인터페이스가 계약을 고정한다.
 *
 * 호출은 **단방향**이다 — Python 은 업무 DB에 접속하지 않고, 이쪽이 필요한 문맥을 전부 실어 보낸다.
 * 그래서 utterances 를 매번 통째로 넘긴다(상태 없는 계산 서버).
 *
 * 이 포트가 던지는 실패는 AiLayerException 으로 통일한다. 구현체가 RestClient 예외를 그대로
 * 흘리면 오케스트레이터가 HTTP 라이브러리에 묶이고, 재시도 여부 판정도 그쪽으로 샌다.
 */
public interface AiLayerPort {

    /*
     * AI-02 · L1.5 지시어 해소. 회의당 한 번 부른다.
     *
     * 주제별로 나눠 부르지 않는 이유: 선행사는 주제 경계를 넘는다("아까 그 얘기"). 주제 안에서만
     * 찾으면 경계를 넘는 지시어가 통째로 UNRESOLVED 가 되는데, 그건 계층이 기권한 것이 아니라
     * 우리가 문맥을 잘라 보낸 결과다.
     *
     * 그래서 L2 **앞에** 돈다. 해소 결과가 L2·L3·L4 가 보는 발화에 반영되어야 하고, 순서를
     * 뒤집으면 L4 가 이미 담당자를 정한 뒤에 대명사가 풀린다.
     *
     * @param targetUtteranceIds 해소 대상. 비워 보내면 전체 발화를 대상으로 본다 —
     *                           지시어 후보를 추리는 코드가 아직 없어 지금은 항상 비운다.
     */
    ResolveReferenceResult resolveReference(
            long tenantId,
            long meetingId,
            List<Utterance> utterances,
            List<Long> targetUtteranceIds,
            List<Participant> participants
    );

    /* AI-03 · L2 주제 분할. 오버랩 3발화는 Python 후처리가 붙여서 돌려준다. */
    SegmentTopicsResult segmentTopics(long tenantId, long meetingId, List<Utterance> utterances);

    /*
     * AI-04 · L3 주제별 정리. 주제마다 한 번씩 부른다(명세 「주제별 N회」).
     *
     * utterances 는 그 주제의 발화만 넘긴다 — 회의 전체를 넘기면 주제를 나눈 의미가 없고
     * 토큰만 주제 수만큼 곱해진다.
     */
    SummarizeTopicResult summarizeTopic(
            long tenantId,
            long meetingId,
            int topicSeq,
            String topic,
            List<Utterance> utterances,
            List<Participant> participants
    );

    /*
     * AI-05 · L3.5 확정/논의 게이트. 주제마다 한 번 부른다.
     *
     * L3 산출을 **저장한 뒤에** 부른다. 판정을 meeting_decision.gate_status 에 적어야 하고,
     * 저장 전에 부르면 응답을 어느 행에 적용할지 임시 순번으로 맞춰야 한다 — 그 맞추기가
     * 틀리면 A 항목의 판정이 B 항목에 저장되는데, 조회는 성공하므로 아무도 오류를 못 본다.
     *
     * @param candidates 판정 대상. 근거 발화가 없는 항목은 넣을 수 없다(GateCandidate 주석).
     */
    GateResult gate(
            long tenantId,
            long meetingId,
            String topic,
            List<GateCandidate> candidates,
            List<Utterance> utterances,
            List<Participant> participants
    );

    /*
     * AI-06 · L4 assignment tuple 추출. 주제마다 한 번 부른다(명세 「세그먼트별 호출」).
     *
     * items 는 **L3.5 가 CONFIRMED 로 판정한 항목만** 넣는다. Python 쪽 요청 스키마가
     * gateStatus 를 Literal["CONFIRMED"] 로 요구하므로 다른 값은 422 로 거절되는데,
     * 거절되기 때문에 안전한 것이 아니라 **거절되도록 만들어 둔 것**이다 — 게이트를 지나지
     * 않은 항목으로 tuple 을 뽑으면 아직 합의도 안 된 논의가 담당자에게 배정된다.
     *
     * @param meetingDate 상대 표현("다음 주까지")을 절대 날짜로 바꾸는 기준점. null 이면
     *                    계층이 상대 표현을 계산하지 않고 dueDate 를 null 로 둔다 — 기준일을
     *                    모르는 채 계산하면 그럴듯하게 틀린 마감이 보드에 꽂힌다.
     */
    ExtractTuplesResult extractTuples(
            long tenantId,
            long meetingId,
            String topic,
            List<ConfirmedItem> items,
            List<Utterance> utterances,
            List<Participant> participants,
            LocalDate meetingDate
    );

    /*
     * AI-07 · L5 관점 다변화 검증. **tuple 마다 한 번** 부른다.
     *
     * 주제별이 아니라 tuple 별인 이유: 검증 대상이 tuple 하나이고, 판정 결과도 그 행에 적힌다.
     * 주제 단위로 묶어 부르면 응답의 판정을 어느 tuple 에 적용할지 다시 맞춰야 하는데,
     * 그 맞추기가 틀리면 A 배정의 검증 결과가 B 배정에 저장된다(L3.5 를 L3 저장 후에
     * 부르는 것과 같은 이유다).
     *
     * <h2>두 관점을 여기서 합치지 않는다</h2>
     * Python 이 EXTRACT_NARROW(앞뒤 3발화만 보고 다시 뽑기)와 VERIFY("이 tuple 이 맞나?")를
     * 내부에서 병렬로 돌리고 조합까지 끝내서 준다. Spring 이 각각 호출해 결과를 모으면
     * 인스턴스 간 왕복이 두 번이 되고, 더 나쁘게는 조합 규칙("한쪽만 실패하면 안전한 쪽으로")이
     * Spring 과 Python 두 곳에 생겨 한쪽만 고쳐지는 상태가 만들어진다.
     *
     * 두 관점이 **모두** 실패하면 계층 실패로 던져진다 — 검증이 아예 수행되지 않은 것을
     * agree=false 로 돌려주면 "관점이 갈렸다"로 기록돼 검증이 돈 것처럼 보인다.
     *
     * @param tuple 검증 대상. L4 가 뽑아 이미 저장된 행의 내용이다.
     * @param items 좁은 시야 재추출에 쓸 확정 항목. L4 에 넘긴 것과 같은 집합이어야 한다 —
     *              다르면 두 관점이 서로 다른 입력을 본 것이라 불일치가 관점 차이인지
     *              입력 차이인지 구분되지 않는다.
     */
    VerifyTupleResult verifyTuple(
            long tenantId,
            long meetingId,
            String topic,
            AssignmentTuple tuple,
            List<ConfirmedItem> items,
            List<Utterance> utterances,
            List<Participant> participants,
            LocalDate meetingDate
    );

    /*
     * 계층의 닫힌 목록에 들어갈 참석자다. personId 가 null 인 항목이 unknown_person 탈출구다.
     * 탈출구가 없으면 모델이 명단 안에서 억지로 하나를 고른다(명세 AI-06).
     */
    record Participant(Long personId, String name) {
    }

    /*
     * L3.5 게이트에 넘길 판정 대상 하나.
     *
     * evidenceUtteranceId 가 필수다. Python 쪽 GateCandidate 가 non-nullable int 로 받으므로
     * null 을 실으면 422 다. 그래서 근거 없는 항목(자막 폴백·사람이 직접 추가한 항목)은
     * 게이트를 부르지 않고 미판정(NULL)으로 남는다 — 근거를 확인할 수 없는 항목이
     * 확정으로 올라가지 않는 쪽이 안전한 방향이다.
     *
     * decisionId 가 곧 itemKey 다. 응답을 이 값으로 되짚는다.
     */
    record GateCandidate(Long decisionId, ItemType itemType, String content, long evidenceUtteranceId) {
    }

    /*
     * L4 에 넘길 확정 항목 하나.
     *
     * gateStatus 를 필드로 들고 다닌다 — 호출자가 CONFIRMED 만 넣었다고 주석으로 약속하는
     * 대신 값으로 확인할 수 있게 둔다. 어댑터가 이 값을 그대로 실어 보내므로, CONFIRMED 가
     * 아닌 것이 섞이면 Python 이 422 로 거절한다. 조용히 통과하는 경로를 만들지 않는다.
     *
     * evidenceUtteranceIds 는 이 항목의 근거 발화다. 채워 보내면 tuple 의 근거도 그 안에서만
     * 고를 수 있게 좁혀진다(Python 이 응답 스키마 enum 으로 박는다).
     */
    record ConfirmedItem(ItemType itemType, GateStatus gateStatus, String content,
                         List<Long> evidenceUtteranceIds) {
    }
}
