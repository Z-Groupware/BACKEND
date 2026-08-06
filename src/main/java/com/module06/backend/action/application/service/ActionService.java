package com.module06.backend.action.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.action.application.command.CreateActionCommand;
import com.module06.backend.action.application.usecase.BulkUpdateActionStatusUseCase;
import com.module06.backend.action.application.usecase.CreateActionUseCase;
import com.module06.backend.action.application.usecase.GetActionDetailUseCase;
import com.module06.backend.action.application.usecase.GetActionsByMeetingUseCase;
import com.module06.backend.action.application.usecase.GetMyActionsUseCase;
import com.module06.backend.action.application.usecase.ReviewActionUseCase;
import com.module06.backend.action.application.usecase.UpdateActionStatusUseCase;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.policy.ActionTypeShapePolicy;
import com.module06.backend.action.domain.repository.ActionRepository;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.project.application.port.ProjectQueryPort;

import lombok.RequiredArgsConstructor;

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

    create(FR-AC-01 예외 경로)는 project(C)가 선언한 ProjectQueryPort로 프로젝트가 같은 회사
    소속이고 활성 상태인지만 확인한다 — meeting(D)이 회의 개설 시 쓰는 것과 동일한 검증이다.
    teamId·assigneeMemberId가 실제로 같은 회사 소속인지까지는 검증하지 않는다(FE가 이미
    소속 목록에서만 고르게 하고, AI 분배 경로도 이 검증까지는 하지 않는 것과 동일 수준).

    연결된 클래스
    - CreateActionUseCase · GetMyActionsUseCase · GetActionDetailUseCase · UpdateActionStatusUseCase ·
      BulkUpdateActionStatusUseCase · ReviewActionUseCase · GetActionsByMeetingUseCase : 구현하는 계약
    - PersonalActionAssigneeOnlyPolicy : 담당자 본인 검사
    - ActionTypeShapePolicy            : 수동 생성 시 종류별 필드 조합 검증 (domain.policy)
    - ActionRepository                 : 저장·조회
    - ProjectQueryPort                 : project(C)가 선언한 프로젝트 존재·활성 검증 포트
*/
@Service
@RequiredArgsConstructor
public class ActionService implements
        CreateActionUseCase,
        GetMyActionsUseCase,
        GetActionDetailUseCase,
        UpdateActionStatusUseCase,
        BulkUpdateActionStatusUseCase,
        ReviewActionUseCase,
        GetActionsByMeetingUseCase {

    // 상태 없는 순수 규칙이라 빈으로 띄우지 않는다 — domain 계층에 스프링 애노테이션을 넣지 않기 위함(절대규칙 5항).
    private static final ActionTypeShapePolicy ACTION_TYPE_SHAPE_POLICY = new ActionTypeShapePolicy();

    private final ActionRepository actionRepository;
    private final ProjectQueryPort projectQueryPort;

    @Override
    @Transactional
    public Action create(CreateActionCommand command) {
        if (!projectQueryPort.existsActiveProject(command.companyId(), command.projectId())) {
            throw new BusinessException(ActionErrorCode.ACTION_PROJECT_NOT_FOUND);
        }

        ACTION_TYPE_SHAPE_POLICY.check(command.actionType(), command.teamId(), command.assigneeMemberId());

        Action action = Action.createManual(
                command.companyId(),
                command.projectId(),
                command.teamId(),
                command.assigneeMemberId(),
                command.actionType(),
                command.title(),
                command.description(),
                command.dueDate()
        );

        return actionRepository.save(action);
    }
}
