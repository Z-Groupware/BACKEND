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

    알려진 한계(2026-08-10, CodeRabbit(#313) 지적, 의도적으로 이번 PR 범위 밖으로 미룸):
    ① issueUploadUrl의 presigned PUT은 fileSize로 실제 업로드 크기를 제한하지 않는다 —
       강제하려면 presigned POST(content-length-range 조건)로 바꿔야 하는데, 이건 FE 업로드
       방식 자체를 바꾸는 계약 변경이라 별도 작업 필요. 파일 크기·확장자 정책은 애초에
       storage(F) 소관으로 합의됐던 영역이다(C는 shape 검증만).
       ② confirm이 실제 S3에 객체가 존재하는지(HeadObject)까지는 확인하지 않는다 — CAP의
       objectMatches와 같은 안전장치가 없다. requireOwnAttachmentKey(confirm 쪽 검증)로 최소
       "자기 프로젝트 네임스페이스 밖 키는 못 씀"만 막았고, "업로드를 안 했는데 확정"까지는
       막지 않는다.
    둘 다 인프라 신설(presigned POST 발급 로직 교체, HeadObject 검증)이 필요한 별도 작업으로
    판단 — 이 PR은 confirm의 크로스 프로젝트/도메인 키 주입(가장 심각한 것)만 막는다.
*/
public interface ProjectAttachmentStoragePort {

    IssuedUploadUrl issueUploadUrl(String s3Key, long fileSize);

    void deleteObject(String fileUrl);

    record IssuedUploadUrl(String uploadUrl, String fileUrl) {
    }
}
