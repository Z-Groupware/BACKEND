package com.module06.backend.action.application.usecase;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.AttachmentReference;

/* comment.
    FR-AC-06 — 팀 액션 상세 조회 기능 계약. teamActionId가 전역 고유키라 전 구성원 공개다.
    소속 프로젝트의 첨부파일도 함께 포함해서 내려준다.
    회사 스코프는 companyId로 다시 확인한다 — 다른 회사 팀 액션 id를 넣으면 존재하지 않는 것과
    같은 404로 덮는다(GetActionDetailUseCase와 동일 판단, #100).
    ?tab=timeline(FR-AC-08, 하위 개인 액션 타임라인)은 아직 배선하지 않는다 — 별도 착수.

    2026-08-11 — 이홍근(FE) 요청으로 4개 값 추가. TEAM 액션은 담당자(assigneeMemberId)를
    아예 저장하지 않는다(ActionTypeShapePolicy가 막음) — 그런데도 assigneeName·assigneeRoleLabel을
    두는 이유를 홍근이 명확히 함: "어느 팀 소속인지 더 쉽게 구분하려고" 팀장을 그 팀 액션의
    사실상 담당자처럼 보여주고 싶다는 것("홍길동(개발팀장)" 표시). 그래서 여기서 저장된 값을
    노출하는 게 아니라 team.leader_member_id로 그때그때 유도한다 — 팀장 공석(정상 상태, DB가
    nullable로 명시)이면 둘 다 null. assigneeRoleLabel은 role(구 sub_team) 테이블의 개인
    역할 태그가 아니라 "{팀명}장" 고정 포맷이다(개인 액션의 assigneeRoleLabel과는 다른 개념).
    sourceMeetingTitle·sourceMeetingScheduledAt은 개인 액션 상세와 동일한 MeetingReference
    조인 재사용 — source_meeting_id는 TEAM/PERSONAL 공용 컬럼이라 새로 필요한 게 없다.

    연결된 클래스
    - ActionRepository            : 조회
    - ActionReferenceRepository   : 프로젝트태그·팀명(+팀장)·출처회의·첨부파일 조인
    - TeamActionDetailResponse    : 출력 DTO (presentation)
    - TeamActionController        : 호출자 (presentation)
*/
public interface GetTeamActionDetailUseCase {

    TeamActionDetail getTeamActionDetail(Long companyId, Long teamActionId);

    record TeamActionDetail(
            Action action,
            String projectTag,
            String teamName,
            String assigneeName,
            String assigneeRoleLabel,
            String sourceMeetingTitle,
            LocalDateTime sourceMeetingScheduledAt,
            List<AttachmentReference> attachments
    ) {
    }
}
