package com.module06.backend.action.application.usecase;

import com.module06.backend.action.domain.model.Action;

/* comment.
    FR-AC-02 — 개인 액션 상세 조회 기능 계약. 전 구성원에게 공개된다(조회 전용 —
    Figma 확인 결과 상태변경 컨트롤이 상세 화면에 없다, 2026-08-07).
    담당자 이름·소속팀명·프로젝트 태그/이름·상위 팀 액션·출처 회의까지 조인해서 내려준다.
    회사 스코프는 companyId로 다시 확인한다 — 다른 회사 액션 id를 넣으면 존재하지 않는 것과
    같은 404로 덮는다(ActionReviewApplyAdapter와 동일한 판단, #100).

    연결된 클래스
    - ActionRepository          : 조회
    - ActionReferenceRepository : 담당자·소속팀·프로젝트·출처회의 조인
    - ActionDetailResponse      : 출력 DTO (presentation)
    - ActionController          : 호출자 (presentation)
*/
public interface GetActionDetailUseCase {

    ActionDetail getActionDetail(Long companyId, Long actionId);

    record ActionDetail(
            Action action,
            String assigneeName,
            String projectTag,
            String projectName,
            String teamName,
            String sourceMeetingTitle,
            String parentActionTitle
    ) {
    }
}
