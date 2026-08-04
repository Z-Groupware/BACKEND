package com.module06.backend.project.application.port;

/* comment.
    project(C)와 storage(F, 김현지) 사이의 경계. 업로드 URL 발급과 오브젝트 삭제 두 가지만 다룬다.
    F가 실제 S3 어댑터를 낼 때까지 로컬 stub으로 개발한다.
*/
public interface ProjectAttachmentStoragePort {

    IssuedUploadUrl issueUploadUrl(String fileName, long fileSize);

    void deleteObject(String fileUrl);

    record IssuedUploadUrl(String uploadUrl, String fileUrl) {
    }
}
