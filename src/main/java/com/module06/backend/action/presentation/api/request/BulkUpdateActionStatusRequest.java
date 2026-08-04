package com.module06.backend.action.presentation.api.request;

/* comment.
    개인·팀 액션 보드 "저장" 버튼의 일괄 상태 변경 요청 DTO(FR-AC-03, FR-AC-07 공용).
    담을 값: (액션 id, 변경할 상태값) 목록. All-or-nothing으로 처리한다.

    연결된 클래스
    - ActionController · TeamActionController                                    : 이 DTO를 받는 진입점
    - BulkUpdateActionStatusCommand · BulkUpdateTeamActionStatusCommand           : 이 DTO가 변환되는 application 명령
*/
public record BulkUpdateActionStatusRequest() {
}
