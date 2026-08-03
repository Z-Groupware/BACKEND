package com.module06.backend.project.application.command;

/* comment.
    OWNER 프로젝트 보드의 "저장" 버튼용 일괄 상태 변경 명령.
    FE가 카드를 드래그로 옮긴 뒤 한 번에 커밋하므로 {projectId, status} 쌍의 목록을 담는다.
    전부 성공 또는 전부 실패(all-or-nothing) — 일부만 반영되면 보드와 DB가 어긋난다.

    연결된 클래스
    - BulkUpdateProjectStatusRequest  : 이 명령으로 변환되는 요청 DTO (presentation)
    - BulkUpdateProjectStatusUseCase  : 이 명령을 받는 기능 계약
    - BulkUpdateProjectStatusService  : 이 명령을 처리하는 구현체
    - ProjectStatus                   : 각 항목이 지정하는 상태 값
*/
public record BulkUpdateProjectStatusCommand() {
}
