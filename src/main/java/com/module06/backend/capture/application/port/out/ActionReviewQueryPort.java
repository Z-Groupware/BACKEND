package com.module06.backend.capture.application.port.out;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.capture.domain.model.AssigneeSource;
import com.module06.backend.capture.domain.model.GateSignals;

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
            String topic,
            boolean manual,
            String reviewStatus,
            Evidence evidence,
            GateSignals signals,
            Boolean autoConfirmed
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
