package com.module06.backend.project.infrastructure.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.module06.backend.project.application.port.ProjectAttachmentStoragePort;

/* comment.
    ProjectAttachmentStoragePort 임시 구현체. F의 S3 어댑터가 들어오면 제거 대상 — 그래서
    @Profile("!prod")로 운영에서만 확실히 안 뜨게 막는다(로컬 프로파일 이름이 아직 미확정이라
    허용목록이 아니라 차단목록 방식으로 감).
    실제 파일 이동 없음: 가짜 URL 문자열을 만들어 돌려주고, 삭제는 항상 성공 처리한다.
    s3Key는 이미 ProjectAttachmentService가 회사·프로젝트 접두까지 붙여 넘긴다 — 여기서 더
    가공하지 않는다.
*/
@Component
@Profile("!prod")
public class ProjectAttachmentStorageStubAdapter implements ProjectAttachmentStoragePort {

    @Override
    public IssuedUploadUrl issueUploadUrl(String s3Key, long fileSize) {
        return new IssuedUploadUrl(
                "https://stub-storage.local/upload/" + s3Key,
                s3Key
        );
    }

    @Override
    public void deleteObject(String fileUrl) {
        // 스텁이라 아무것도 안 함 — 실제 F 어댑터가 붙으면 여기서 진짜 삭제 호출
    }
}
