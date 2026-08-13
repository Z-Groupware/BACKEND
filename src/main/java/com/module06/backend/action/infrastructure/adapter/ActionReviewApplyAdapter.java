package com.module06.backend.action.infrastructure.adapter;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionReviewStatus;
import com.module06.backend.action.domain.repository.ActionReferenceRepository;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.capture.application.port.out.ActionReviewApplyPort;
import com.module06.backend.global.exception.BusinessException;

/* comment.
    action(C)이 구현하는 검토(A) 아웃바운드 포트. A가 정의한 ActionReviewApplyPort
    (capture.application.port.out)를 실제로 배선한다 — 인수인계(E)의 ActionReassignPort를
    ActionReassignAdapter가 배선한 것과 같은 방향이다(2026-08-06, RVW-02 착수).

    A가 action을 직접 UPDATE하지 않는 이유: 상태 전이 규칙(담당자 없는 PERSONAL을 막는 것 ·
    확정 시각을 찍는 것 · 기한을 고치면 dueDateDefaulted를 내리는 것)이 Action 애그리거트의
    것이고, 그 규칙을 지나지 않는 쓰기 경로가 생기면 규칙이 두 곳에 흩어진다.

    회사 스코프를 다시 확인한다. A의 조회가 이미 company_id로 걸러 오지만, 이 포트는 공개된
    인바운드 경계이므로 여기서도 막는다 — 한 곳이 빠지면 그 경로만 조용히 뚫린다(#100).

    연결된 클래스
    - ActionReviewApplyPort : 구현하는 계약 (capture.application.port.out)
    - Action                : applyHumanReview로 판정을 반영하는 애그리거트
    - ActionRepository      : 조회·저장 위임 대상
    - ActionErrorCode       : 미존재·회사 불일치에 쓰는 코드 (action.exception)
*/
@Component
@RequiredArgsConstructor
public class ActionReviewApplyAdapter implements ActionReviewApplyPort {

    private final ActionRepository actionRepository;

    /*
     * 예정 시작일 범위검증의 상한(프로젝트 마감일)을 여기서 꺼낸다.
     *
     * A 가 넘겨주지 않는 이유는 포트 주석에 있다 — 그 값은 project 소유이고 A 는 갖고 있지
     * 않다. A 가 굳이 조회해 넘기면 검증 기준선을 A 가 정하게 되고, 낡은 값으로 통과·거절이
     * 갈린다. 분배 경로가 기한 기본값을 채울 때 쓰는 것과 같은 조회다(ActionDistributionService).
     */
    private final ActionReferenceRepository actionReferenceRepository;

    @Override
    @Transactional
    public void apply(long companyId, long actionId, Long assigneeMemberId, Long teamId, LocalDate dueDate,
                       String title, String detail, LocalDate plannedStartDate, String reviewStatus) {
        /*
         * 둘 다 ACTION_NOT_FOUND(404)다. 자바 기본 예외를 던지면 회사 불일치가 500 으로,
         * 액션 미존재가 400 으로 나가 둘 다 명세와 갈린다(GlobalExceptionHandler 의 매핑).
         * 다른 회사 액션에 403·500 을 주면 "그 액션은 존재한다"가 새어 나가므로 미존재와 같은
         * 응답으로 덮는다 — capture 의 MEETING_404_2 와 같은 판단이다(#100).
         */
        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new BusinessException(ActionErrorCode.ACTION_NOT_FOUND));

        if (!Long.valueOf(companyId).equals(action.getCompanyId())) {
            throw new BusinessException(ActionErrorCode.ACTION_NOT_FOUND);
        }

        /*
         * 2026-08-13 — teamId도 같은 회사 소속인지 확인한다(ActionService#create의
         * existsTeamInCompany와 동일 이유) — 아니면 다른 회사 팀에 액션을 붙이는 IDOR이 된다.
         * A가 넘겨준 값이라도 신뢰하지 않는다 — 이 포트가 공개된 인바운드 경계이기 때문이다
         * (클래스 주석 "회사 스코프를 다시 확인한다").
         */
        if (teamId != null && !actionReferenceRepository.existsTeamInCompany(teamId, companyId)) {
            throw new BusinessException(ActionErrorCode.ACTION_TEAM_NOT_FOUND);
        }

        /*
         * 프로젝트 마감일은 **예정 시작일이 실제로 올 때만** 꺼낸다.
         *
         * 항상 조회하면 예정 시작일과 무관한 판정(담당자만 고치는 확정 · 반려)에도 쿼리가
         * 한 번 더 나간다. applyHumanReview 도 plannedStartDate 가 null 이면 이 값을 보지
         * 않으므로(그쪽 검증 분기) 없을 때 넘기지 않는 것이 계약과 맞는다.
         */
        LocalDate projectDueDate = plannedStartDate == null ? null : projectDueDateOf(action);

        // detail → Action.description. 파라미터명이 갈리는 이유는 ActionReviewApplyPort 주석 참고.
        action.applyHumanReview(
                assigneeMemberId, teamId, dueDate, ActionReviewStatus.valueOf(reviewStatus), title, detail,
                plannedStartDate, projectDueDate);
        actionRepository.save(action);
    }

    /*
     * 이 액션이 속한 프로젝트의 마감일.
     *
     * 프로젝트가 없는 액션(projectId == null)이면 null 이다. 그 경우 applyHumanReview 가
     * IllegalArgumentException 을 던진다 — 상한을 모르는 채로 통과시키면 범위검증이 이름만
     * 남고, 검토 화면이 어떤 날짜든 받아들이게 된다. 막는 쪽이 맞다.
     */
    private LocalDate projectDueDateOf(Action action) {
        if (action.getProjectId() == null) {
            return null;
        }
        return actionReferenceRepository.findProjectReferences(List.of(action.getProjectId())).stream()
                .findFirst()
                .map(ActionReferenceRepository.ProjectReference::dueDate)
                .orElse(null);
    }
}
