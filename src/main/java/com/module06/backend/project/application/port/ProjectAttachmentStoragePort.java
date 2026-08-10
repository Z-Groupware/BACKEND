package com.module06.backend.project.application.port;

/* comment.
    project(C)와 storage(F, 김현지) 사이의 경계. 업로드 URL 발급과 오브젝트 삭제 두 가지만 다룬다.
    CapObjectStoragePort와 동일 패턴 — s3Key는 호출자(ProjectAttachmentService)가 companyId·
    projectId로 미리 조립해서 넘긴다. 이 Port·구현체는 완성된 키만 다루고 네이밍 규칙은 모른다.
    구현체는 두 개 — 로컬/테스트는 ProjectAttachmentStorageStubAdapter(@Profile("!prod")),
    운영은 ProjectAttachmentS3StorageAdapter(@Profile("prod")).

    IssuedUploadUrl.fileUrl은 브라우저로 열리는 실제 URL이 아니라 S3 오브젝트 키(식별자)다 —
    버킷이 비공개라 조회 기능은 이번 스코프 밖(업로드·삭제만). deleteObject도 같은 값을 그대로
    받아 키로 쓴다.
*/
public interface ProjectAttachmentStoragePort {

    IssuedUploadUrl issueUploadUrl(String s3Key, long fileSize);

    void deleteObject(String fileUrl);

    record IssuedUploadUrl(String uploadUrl, String fileUrl) {
    }
}
