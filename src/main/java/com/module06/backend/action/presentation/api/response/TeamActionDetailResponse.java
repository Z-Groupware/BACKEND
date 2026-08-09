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

    연결된 클래스
    - TeamActionController   : 이 DTO를 내보내는 진입점
    - TeamActionService       : 이 DTO를 만드는 구현체
    - ProjectReferenceEntity  : 소속 프로젝트 첨부파일 조인 (infrastructure.persistence)
*/
public record TeamActionDetailResponse(
        Long id,
        String title,
        String description,
        ActionStatus status,
        LocalDate dueDate,
        String projectTag,
        String teamName,
        List<Attachment> attachments
) {

    public static TeamActionDetailResponse from(TeamActionDetail detail) {
        Action action = detail.action();

        return new TeamActionDetailResponse(
                action.getId(),
                action.getTitle(),
                action.getDescription(),
                action.getStatus(),
                action.getDueDate(),
                detail.projectTag(),
                detail.teamName(),
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
