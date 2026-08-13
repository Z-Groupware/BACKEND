package com.module06.backend.action.application.usecase;

import com.module06.backend.action.domain.model.ActionStatus;

/* comment.
    2026-08-13, 종준님(PO) 확정 — OWNER·ADMIN이 회사 전체 범위에서 특정 구성원의 개인 액션을
    조회하는 계약. GetMyActionsUseCase의 targetMemberId(LEADER 전용, 팀 스코프)와 같은 필터
    의미("특정 담당자로 좁힌다")를 회사 스코프로 확장한 것이지만, 그 인터페이스 자체는 건드리지
    않는다(2026-08-11 주석에 이미 "OWNER 분기는 만들지 않는다"고 명시돼 있었음 — 이번이 그
    보류됐던 OWNER/ADMIN용 화면이다). Figma "회원 관리" 화면(LEADER의 "팀원 관리"와 별개) 대응.

    assigneeMemberId는 필수다 — 이 화면은 항상 특정 구성원 한 명의 액션을 보는 용도라
    null(회사 전체 미필터 조회)은 이번 스코프에 없다.

    연결된 클래스
    - ActionRepository.findAllByAssigneeMemberId/countByAssigneeMemberId : 조회 (기존 메서드 재사용, 변경 없음)
    - ActionReferenceRepository.existsMemberInCompany                    : 대상이 같은 회사 소속인지 검증
    - GetMyActionsUseCase.ActionListResult/ActionListItem                : 응답 형태 재사용
    - CompanyActionController                                            : 호출자 (presentation)
*/
public interface GetCompanyMemberActionsUseCase {

    GetMyActionsUseCase.ActionListResult getCompanyMemberActions(
            Long companyId, Long assigneeMemberId, ActionStatus status, Boolean overdue,
            String sort, String order, int page, int size);
}
