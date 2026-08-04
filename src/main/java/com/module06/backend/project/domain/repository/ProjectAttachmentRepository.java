package com.module06.backend.project.domain.repository;

import java.util.List;
import java.util.Optional;

import com.module06.backend.project.domain.model.ProjectAttachment;

/* comment.
    프로젝트 첨부파일 메타데이터 저장소 계약. 오브젝트 스토리지 접근은 여기 책임이 아니다
    — 그건 ProjectAttachmentStoragePort가 맡는다.
*/
public interface ProjectAttachmentRepository {

    ProjectAttachment save(ProjectAttachment attachment);

    Optional<ProjectAttachment> findById(Long id);

    List<ProjectAttachment> findAllByProjectId(Long projectId);

    void deleteById(Long id);
}
