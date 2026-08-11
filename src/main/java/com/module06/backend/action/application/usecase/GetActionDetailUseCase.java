package com.module06.backend.action.application.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.module06.backend.action.domain.model.Action;

/* comment.
    FR-AC-02 — 개인 액션 상세 조회 기능 계약. 전 구성원에게 공개된다(조회 전용 —
    Figma 확인 결과 상태변경 컨트롤이 상세 화면에 없다, 2026-08-07).
    담당자 이름·소속팀명·프로젝트 태그/이름·상위 팀 액션·출처 회의까지 조인해서 내려준다.
    회사 스코프는 companyId로 다시 확인한다 — 다른 회사 액션 id를 넣으면 존재하지 않는 것과
    같은 404로 덮는다(ActionReviewApplyAdapter와 동일한 판단, #100).

    2026-08-11 — 이홍근(FE) 상세 화면 대조로 4개 값 추가: assigneeRoleLabel(담당자의
    sub_team, "역할" — team과 달리 리더 없는 순수 분류 태그, 미지정이면 null),
    sourceMeetingScheduledAt(meeting.start_at), parentActionTeamName·parentActionDueDate
    (상위 TEAM 액션 자신의 teamId·dueDate). projectId는 Action 엔티티에 이미 있어 별도
    조인 없이 presentation에서 바로 꺼내 쓴다.

    연결된 클래스
    - ActionRepository          : 조회
    - ActionReferenceRepository : 담당자·역할·소속팀·프로젝트·출처회의 조인
    - ActionDetailResponse      : 출력 DTO (presentation)
    - ActionController          : 호출자 (presentation)
*/
public interface GetActionDetailUseCase {

    ActionDetail getActionDetail(Long companyId, Long actionId);

    record ActionDetail(
            Action action,
            String assigneeName,
            String assigneeRoleLabel,
            String projectTag,
            String projectName,
            String teamName,
            String sourceMeetingTitle,
            LocalDateTime sourceMeetingScheduledAt,
            String parentActionTitle,
            String parentActionTeamName,
            LocalDate parentActionDueDate
    ) {
    }
}
