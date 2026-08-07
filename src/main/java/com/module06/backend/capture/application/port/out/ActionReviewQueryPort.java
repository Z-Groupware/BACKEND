package com.module06.backend.capture.application.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.GateSignals;
import com.module06.backend.capture.domain.model.RejectReason;

/*
 * RVW-01 검토 조회가 쓰는 읽기 포트다.
 *
 * <h2>action 은 C 도메인 소유다 — 읽기만 한다</h2>
 * 분배(ActionDistributionPort)로 만들어진 action 행을 A 가 화면용으로 다시 읽는다.
 * JPA 엔티티로 매핑해 연관관계를 만들지 않는 이유는 MeetingParticipantJdbcProvider 와 같다 —
 * 그쪽 스키마 변경이 이쪽을 깨뜨리고, 반대로 이쪽이 그 테이블에 쓰기를 할 수 있게 된다.
 *
 * <h2>왜 A 가 이 화면을 갖나</h2>
 * 검토 화면은 **분석 결과를 사람이 확인하는 자리**이고, 보여줄 것의 대부분(주제 · 근거 발화 ·
 * 게이트 신호)이 A 의 산출물이다. action 에서 오는 것은 제목·담당자·상태뿐이다.
 */
public interface ActionReviewQueryPort {

    /*
     * 회의의 검토 대상을 읽는다.
     *
     * companyId 를 **인자로 받는다.** meetingId 만으로 조회하면 다른 회사 회의의 액션과
     * 근거 발화가 나간다 — 메타데이터가 아니라 회의 내용이다(CWE-639 · #100 사고 지점).
     *
     * @param reviewStatus 필터. null 이면 전체를 준다(명세: 생략 시 전체)
     */
    List<ReviewAction> findByMeeting(long companyId, long meetingId, String reviewStatus);

    /*
     * 검토 화면에 뿌릴 액션 하나.
     *
     * <h2>gate 가 null 일 수 있다</h2>
     * 사람이 직접 추가한 액션(RVW-03 · isManual=true)은 게이트를 지나지 않았으므로 신호가
     * 없다. 0 이나 false 로 채우지 않는다 — "게이트가 떨어뜨렸다"와 "게이트를 안 지났다"는
     * 다른 상태이고, 뭉치면 화면이 수동 추가 건을 AI 가 의심한 것처럼 보여준다.
     *
     * <h2>evidence 도 null 일 수 있다</h2>
     * 같은 이유다. 수동 추가는 근거 발화가 없을 수 있고, 그때는 검토 화면이 인용을 비운다.
     *
     * @param topic 이 액션이 나온 주제. action 에는 없고 meeting_assignment_tuple 이 갖는다
     */
    record ReviewAction(
            Long actionId,
            Long assigneeMemberId,
            String assigneeName,
            AssigneeSource assigneeSource,
            String title,
            String detail,
            LocalDate dueDate,
            /*
             * 이 기한이 **회의에서 나온 것이 아니라 프로젝트 마감일로 채운 것**인지.
             *
             * action.due_date 는 NOT NULL 이라 기한을 말하지 않은 액션도 날짜가 채워진다
             * (분배 시 C 가 프로젝트 마감일을 넣고 due_date_defaulted 를 찍는다 · V2.6.4).
             * 이 값을 내려주지 않으면 화면에서 둘이 똑같은 날짜로 보여 **"AI 가 8/31 이라고
             * 판단했구나"로 읽힌다** — 사람이 고쳐야 할 기한을 그냥 넘긴다.
             */
            boolean dueDateDefaulted,
            String topic,
            boolean manual,
            String reviewStatus,
            /*
             * 왜 고쳤는지 · 왜 반려했는지(RVW-02). 아직 판정을 받지 않았으면 null 이다.
             *
             * action 이 아니라 review_log 에서 온다 — 사유는 라벨이고, 액션의 현재 상태가
             * 아니라 사람이 그때 내린 판단이다. 상태값(reviewStatus)만 내려주면 화면에
             * "반려됨"만 뜨고 **다음 사람이 이유를 몰라 다시 물어봐야 한다.**
             */
            RejectReason rejectReason,
            Evidence evidence,
            GateSignals signals,
            Boolean autoConfirmed
    ) {
    }

    /*
     * 판정할 액션 하나를 읽는다(RVW-02).
     *
     * 목록 조회(findByMeeting)와 따로 두는 이유 — 라벨을 남기려면 **AI 가 원래 낸 값**이
     * 필요하고 그건 검토 화면이 쓰지 않는다. tuple 에서 오는 값(llmOutput)을 목록 응답에까지
     * 실으면 액션 수십 건마다 쓰이지 않는 필드가 따라다닌다.
     *
     * 회사·회의를 함께 조건에 넣는다. actionId 만으로 찾으면 **다른 회의의 액션 id 를 넣어
     * 남의 회사 액션을 고칠 수 있다** — 관문(MeetingAccessGuard)은 회의까지만 본다.
     */
    Optional<ReviewTarget> findOne(long companyId, long meetingId, long actionId);

    /*
     * 이 회의를 분배 확정한 시각(RVW-05). 아직 확정하지 않았으면 비어 있다.
     *
     * **회의 단위 값이다.** 액션마다 시각이 있지만 화면이 묻는 것은 "이 회의를 내보냈나"
     * 하나이고, 한 번의 확정으로 나간 액션은 모두 같은 시각을 갖는다. 확정 뒤에 추가된
     * 액션(RVW-03)은 아직 안 나갔으므로 NULL 이고, 그래서 **가장 최근 값**을 본다 —
     * 그게 "마지막으로 내보낸 때"다.
     */
    Optional<LocalDateTime> dispatchedAtOf(long companyId, long meetingId);

    /*
     * 판정 대상 하나. 라벨을 만들 재료가 전부 들어 있다.
     *
     * @param aiValue         AI 가 낸 원본. tuple 에서 읽는다 — action 은 사람이 고친 뒤
     *                        덮여 있을 수 있어서 원본이 아니다. 수동 추가 액션(RVW-03)은
     *                        tuple 이 없어 null 이고, 그때 라벨은 few-shot 풀에서 제외된다
     * @param evidenceContent 근거 발화 원문. 벡터의 임베딩 대상이다(V5.10) — tuple 이 아니라
     *                        발화를 임베딩해야 검색 시점의 쿼리와 같은 공간에 놓인다
     */
    record ReviewTarget(
            Long actionId,
            Long assigneeMemberId,
            LocalDate dueDate,
            String title,
            boolean manual,
            String reviewStatus,
            Long evidenceTranscriptId,
            String evidenceContent,
            String topic,
            AiValue aiValue
    ) {
    }

    /*
     * AI 가 낸 원본 값(review_log.llm_output).
     *
     * 모델·프롬프트 버전을 함께 읽는다. **어느 버전이 틀렸는지 모르면 라벨을 모아도
     * 개선 여부를 비교할 수 없다** — 버전이 올라간 뒤에도 옛 라벨이 섞여 집계된다.
     */
    record AiValue(
            String title,
            Long assigneeMemberId,
            AssigneeSource assigneeSource,
            LocalDate dueDate,
            String modelName,
            String promptVersion
    ) {
    }

    /*
     * 근거 발화. **검토 화면에 함께 내려준다** — 근거 없이 담당자만 고르게 하면 사람이
     * 판단할 재료가 없고, 액션마다 ANLZ-05 를 다시 부르는 것도 낭비다(명세 RVW-01).
     *
     * speakerName 이 null 인 경우가 정상이다. L1 이 화자 판정을 포기한 발화이고,
     * 그때 화면은 인용만 보여준다 — 모르는 이름을 지어내지 않는다.
     */
    record Evidence(
            Long transcriptId,
            String speakerName,
            String content,
            Integer startOffsetMs
    ) {
    }
}
