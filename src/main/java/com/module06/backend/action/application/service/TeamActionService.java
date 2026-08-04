package com.module06.backend.action.application.service;

import com.module06.backend.action.application.usecase.BulkUpdateTeamActionStatusUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionDetailUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionTimelineUseCase;
import com.module06.backend.action.application.usecase.GetTeamActionsUseCase;
import com.module06.backend.action.application.usecase.UpdateTeamActionStatusUseCase;

/* comment.
    팀 액션 리소스(FR-AC-06,07,08)를 다루는 단일 구현체. 쓰기·읽기 트랜잭션 경계는
    메서드별로 갈린다(updateStatus·bulkUpdateStatus는 쓰기, getList·getDetail·getTimeline은
    읽기 전용).
    조회 주체가 명확히 "팀"인 것들(목록=LEADER 스코프, 상세, 하위 개인 액션 타임라인)만
    모았다 — 회의 기준 조회(FR-AC-09)는 ActionService에 남겨뒀다(그쪽 주석 참고).
    UseCase 인터페이스는 엔드포인트 1:1(포트 경계)로 유지하되, 구현체는 같은 리소스를
    다루는 것끼리 이 클래스 하나로 묶었다 — 08/04 팀 협의(윤종호)로 project 도메인에 이어 action에도
    동일 원칙 적용.

    연결된 클래스
    - GetTeamActionsUseCase · GetTeamActionDetailUseCase · GetTeamActionTimelineUseCase ·
      UpdateTeamActionStatusUseCase · BulkUpdateTeamActionStatusUseCase : 구현하는 계약
    - TeamActionLeaderOnlyPolicy : 상태 변경 시 해당 팀 LEADER 검사
    - ActionRepository           : 저장·조회
*/
public class TeamActionService implements
        GetTeamActionsUseCase,
        GetTeamActionDetailUseCase,
        GetTeamActionTimelineUseCase,
        UpdateTeamActionStatusUseCase,
        BulkUpdateTeamActionStatusUseCase {
}
