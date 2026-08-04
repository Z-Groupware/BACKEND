package com.module06.backend.action.presentation.api.request;

/* comment.
    개인·팀 액션 단건 상태 변경 요청 DTO(FR-AC-03, FR-AC-07 공용). 담을 값: 변경할 상태값.
    개인 액션은 ActionController, 팀 액션은 TeamActionController가 각각 받아서 서로 다른
    Command(UpdateActionStatusCommand / UpdateTeamActionStatusCommand)로 변환한다.

    연결된 클래스
    - ActionController · TeamActionController                       : 이 DTO를 받는 진입점
    - UpdateActionStatusCommand · UpdateTeamActionStatusCommand      : 이 DTO가 변환되는 application 명령
*/
public record UpdateActionStatusRequest() {
}
