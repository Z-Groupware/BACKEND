package com.module06.backend.action.application.service;

import com.module06.backend.action.application.usecase.BulkUpdateActionStatusUseCase;
import com.module06.backend.action.application.usecase.CreateActionUseCase;
import com.module06.backend.action.application.usecase.GetActionDetailUseCase;
import com.module06.backend.action.application.usecase.GetActionsByMeetingUseCase;
import com.module06.backend.action.application.usecase.GetMyActionsUseCase;
import com.module06.backend.action.application.usecase.ReviewActionUseCase;
import com.module06.backend.action.application.usecase.UpdateActionStatusUseCase;

/* comment.
    개인 액션 리소스(FR-AC-01,02,03,04,09)를 다루는 단일 구현체. 쓰기·읽기 트랜잭션 경계는
    메서드별로 갈린다(create·updateStatus·bulkUpdateStatus·review는 쓰기, getMy·getDetail·
    getByMeeting은 읽기 전용).
    getActionsByMeeting(FR-AC-09)은 회의 하나에서 개인·팀 액션이 actionType으로 섞여 나오는
    조회라 TeamActionService로 뺄지 애매했는데, 조회 주체가 "회의"지 "팀"이 아니고 개인 액션도
    함께 나오므로 여기 남겼다 — 팀 전용 조회는 전부 TeamActionService로 분리했다.
    UseCase 인터페이스는 엔드포인트 1:1(포트 경계)로 유지하되, 구현체는 같은 리소스를
    다루는 것끼리 이 클래스 하나로 묶었다 — 08/04 팀 협의(윤종호)로 project 도메인에 이어 action에도
    동일 원칙 적용.

    연결된 클래스
    - CreateActionUseCase · GetMyActionsUseCase · GetActionDetailUseCase · UpdateActionStatusUseCase ·
      BulkUpdateActionStatusUseCase · ReviewActionUseCase · GetActionsByMeetingUseCase : 구현하는 계약
    - PersonalActionAssigneeOnlyPolicy : 담당자 본인 검사
    - ActionTypeShapePolicy            : 수동 생성 시 종류별 필드 조합 검증 (domain.policy)
    - ActionRepository                 : 저장·조회
*/
public class ActionService implements
        CreateActionUseCase,
        GetMyActionsUseCase,
        GetActionDetailUseCase,
        UpdateActionStatusUseCase,
        BulkUpdateActionStatusUseCase,
        ReviewActionUseCase,
        GetActionsByMeetingUseCase {
}
