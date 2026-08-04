package com.module06.backend.project.domain.model;

import java.time.LocalDateTime;

import lombok.Getter;

/* comment.
    프로젝트 첨부파일 메타데이터. 바이너리는 storage(F)가 갖고, 여기는 참조만 가진다.
    크기·확장자 제한은 storage 정책이라 여기선 shape 수준(빈 값 여부)만 검증한다.
*/
@Getter
public class ProjectAttachment {

    private final Long id;
    private final Long projectId;
    private final String fileName;
    private final String fileUrl;
    private final long fileSize;
    private final Long uploadedBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private ProjectAttachment(
            Long id,
            Long projectId,
            String fileName,
            String fileUrl,
            long fileSize,
            Long uploadedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        // shape 검증: storage의 실제 크기/확장자 정책이 아니라 빈 값 방지 수준
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName은 비어있을 수 없습니다.");
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("fileUrl은 비어있을 수 없습니다.");
        }
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize는 0 이상이어야 합니다.");
        }

        this.id = id;
        this.projectId = projectId;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.fileSize = fileSize;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // 업로드 확정(ConfirmAttachment) 시점에 신규 생성. id·타임스탬프는 저장소가 채운다.
    public static ProjectAttachment create(Long projectId, String fileName, String fileUrl, long fileSize, Long uploadedBy) {
        return new ProjectAttachment(null, projectId, fileName, fileUrl, fileSize, uploadedBy, null, null);
    }

    // 저장소가 조회 결과를 이 모델로 복원할 때 사용.
    public static ProjectAttachment reconstitute(
            Long id,
            Long projectId,
            String fileName,
            String fileUrl,
            long fileSize,
            Long uploadedBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new ProjectAttachment(id, projectId, fileName, fileUrl, fileSize, uploadedBy, createdAt, updatedAt);
    }

    public boolean isUploadedBy(Long memberId) {
        return this.uploadedBy != null && this.uploadedBy.equals(memberId);
    }
}
