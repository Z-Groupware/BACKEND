package com.module06.backend.action.presentation.api.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.action.application.usecase.GetTeamActionDetailUseCase.TeamActionDetail;
import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.repository.ActionReferenceRepository.AttachmentReference;

/* comment.
    팀 액션 상세 응답 DTO(FR-AC-06). teamActionId가 전역 고유키라 전 구성원 공개다.
    소속 프로젝트의 첨부파일 목록을 인라인으로 함께 담는다(FE가 별도 호출 없이 렌더링).

    첨부파일 항목의 필드 구성은 project 도메인 AttachmentResponse와 같은 shape을 따르되,
    도메인 간 presentation DTO를 직접 참조하지 않는다(0절 1항) — action이 자체 타입으로 복제해서 쓴다.

    2026-08-11 — 이홍근(FE) 요청으로 4개 필드 추가:
    - projectId : URL의 projectId와 대조하는 404 가드용(단순 표시용 아님).
    - assigneeName / assigneeRoleLabel : TEAM 액션은 담당자를 저장하지 않는다 — 그 팀의
      현재 팀장을 유도해 "홍길동(개발팀장)"처럼 보여주기 위한 값이다(팀장 공석이면 둘 다 null).
      개인 액션 상세의 assigneeRoleLabel(role 테이블의 개인 역할 태그)과는 다른 개념이다.
    - sourceMeetingTitle / sourceMeetingScheduledAt : 개인 액션 상세와 동일한 값, TEAM 액션도
      source_meeting_id를 가질 수 있어 추가했다.

    연결된 클래스
    - TeamActionController   : 이 DTO를 내보내는 진입점
    - TeamActionService       : 이 DTO를 만드는 구현체
    - ProjectReferenceEntity  : 소속 프로젝트 첨부파일 조인 (infrastructure.persistence)
*/
public record TeamActionDetailResponse(
        Long id,
        Long projectId,
        String title,
        String description,
        ActionStatus status,
        LocalDate dueDate,
        String projectTag,
        String teamName,
        String assigneeName,
        String assigneeRoleLabel,
        Long sourceMeetingId,
        String sourceMeetingTitle,
        LocalDateTime sourceMeetingScheduledAt,
        List<Attachment> attachments
) {

    public static TeamActionDetailResponse from(TeamActionDetail detail) {
        Action action = detail.action();

        return new TeamActionDetailResponse(
                action.getId(),
                action.getProjectId(),
                action.getTitle(),
                action.getDescription(),
                action.getStatus(),
                action.getDueDate(),
                detail.projectTag(),
                detail.teamName(),
                detail.assigneeName(),
                detail.assigneeRoleLabel(),
                action.getSourceMeetingId(),
                detail.sourceMeetingTitle(),
                detail.sourceMeetingScheduledAt(),
                detail.attachments().stream().map(Attachment::from).toList()
        );
    }

    public record Attachment(Long attachmentId, String fileName, String fileUrl, long fileSize, LocalDateTime createdAt) {
        public static Attachment from(AttachmentReference reference) {
            return new Attachment(
                    reference.attachmentId(),
                    reference.fileName(),
                    reference.fileUrl(),
                    reference.fileSize(),
                    reference.createdAt()
            );
        }
    }
}
