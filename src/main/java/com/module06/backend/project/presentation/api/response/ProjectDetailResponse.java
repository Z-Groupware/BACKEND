package com.module06.backend.project.presentation.api.response;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.project.application.usecase.GetProjectDetailUseCase.ProjectDetailResult;
import com.module06.backend.project.domain.model.ProjectStatus;

/* comment.
    프로젝트 상세(기획 탭) 응답 DTO. 기획(description)을 포함한다 — 전 구성원 공개다.
    첨부파일 목록을 인라인으로 담아서 FE가 다운로드 링크를 바로 그릴 수 있게 한다.

    연결된 클래스
    - ProjectController       : 이 DTO를 내보내는 진입점
    - ProjectService          : 이 DTO를 만드는 구현체
    - AttachmentResponse      : 인라인으로 실리는 첨부파일 항목
*/
public record ProjectDetailResponse(
        Long id,
        String tag,
        String color,
        String name,
        String description,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate dueDate,
        List<Long> teamIds,
        List<AttachmentResponse> attachments
) {

    public static ProjectDetailResponse from(ProjectDetailResult result) {
        var project = result.project();
        return new ProjectDetailResponse(
                project.getId(),
                project.getTag(),
                project.getColor(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getStartDate(),
                project.getDueDate(),
                project.getTeamIds(),
                result.attachments().stream()
                        .map(AttachmentResponse::from)
                        .toList()
        );
    }
}
