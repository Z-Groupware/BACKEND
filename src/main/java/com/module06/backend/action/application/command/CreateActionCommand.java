package com.module06.backend.action.application.command;

/* comment.
    액션 수동 추가 입력값을 application 계층으로 전달하는 명령 객체(FR-AC-01 예외 경로).
    AI가 놓친 액션을 사람이 "+" 버튼으로 넣는 경우에만 쓰인다 — 정상 경로는 ActionDistributionPort.
    담을 값: 프로젝트 id · 액션 종류(TEAM/PERSONAL) · 팀 id 또는 담당자 id · 제목 · 설명 · 마감일.
    Controller의 Request DTO를 그대로 내려보내지 않기 위한 경계 역할이다.

    연결된 클래스
    - CreateActionRequest    : 이 명령으로 변환되는 요청 DTO (presentation)
    - CreateActionUseCase    : 이 명령을 받는 기능 계약
    - ActionService : 이 명령을 처리하는 구현체
    - ActionTypeShapePolicy  : 종류별 필드 조합 검증 (domain.policy)
*/
public record CreateActionCommand() {
}
