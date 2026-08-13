package com.module06.backend.capture.application.port.out;

import java.time.LocalDate;

/*
 * 사람이 직접 추가한 액션을 만드는 아웃바운드 포트다(RVW-03). A 가 선언하고 C(액션)가 배선한다 —
 * {@link ActionReviewApplyPort} · 인수인계의 ActionReassignPort 와 같은 방향이다.
 *
 * <h2>왜 ActionDistributionPort 를 쓰지 않나</h2>
 * 그 포트는 **분석 산출물을 일괄 생성**하는 자리다. 거기로 수동 추가를 태우면 만들어지는 액션이
 * PENDING(검토 대상)이 되는데, 사람이 방금 직접 넣은 항목이 「AI 확인 필요」 묶음에 나타나면
 * 자기가 쓴 것을 자기가 다시 검토하게 된다.
 *
 * <h2>왜 A 가 action 을 직접 INSERT 하지 않나</h2>
 * 액션의 상태 규칙(담당자 없는 PERSONAL 을 막는 것 · 확정 시각을 찍는 것)이 Action 애그리거트의
 * 것이다. 그 규칙을 지나지 않는 쓰기 경로가 생기면 규칙이 두 곳에 흩어진다
 * (ActionReviewApplyAdapter 주석의 판단과 같다).
 */
public interface ReviewActionCreatePort {

    /*
     * @return 채번된 actionId
     */
    long createManual(ManualAction action);

    /*
     * @param sourceMeetingId **빠지면 검토 화면에서 사라진다.** RVW-01 이 회의로 액션을 찾으므로,
     *                        이 값이 없는 액션은 방금 만든 사람 눈에도 보이지 않는다.
     * @param assigneeMemberId teamId 와 상호 배타적이다(2026-08-13, RVW-03 teamId 지원).
     *                         A(AddReviewActionService)가 이미 최소 하나를 보장해 넘긴다.
     * @param teamId 채워지면 TEAM 액션을 만든다. 회사 소속 검증은 여기(C)가 한다 — A가 넘긴
     *               값이라도 신뢰하지 않는다(ActionReviewApplyAdapter의 existsTeamInCompany와
     *               같은 판단, #100).
     */
    record ManualAction(
            long companyId,
            long meetingId,
            long projectId,
            Long assigneeMemberId,
            Long teamId,
            String title,
            String detail,
            LocalDate dueDate,
            Long evidenceTranscriptId
    ) {
    }
}
