package com.module06.backend.project.application.usecase;

/* comment.
    FR-PJ-06 — OWNER 프로젝트 보드의 상태 일괄 변경 기능 계약.
    보드 3단(대기/진행중/완료)에서 드래그한 결과를 "저장" 버튼으로 한 번에 커밋한다.
    상태 전환 자체에 서버 제약은 없다(건너뛰기·되돌리기 허용). 단 all-or-nothing이다.

    연결된 클래스
    - BulkUpdateProjectStatusCommand : 입력
    - BulkUpdateProjectStatusService : 구현체
    - ProjectStatus                  : 지정 가능한 상태 값
*/
public interface BulkUpdateProjectStatusUseCase {
}
