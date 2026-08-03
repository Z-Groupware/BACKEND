package com.module06.backend.project.application.port;

/* comment.
    project(C)와 storage(F, 김현지) 사이의 경계. C가 계약을 선언하고 F가 구현한다.
    책임은 업로드 URL 발급(issueUploadUrl)과 오브젝트 삭제(deleteObject) 두 가지.
    파일 크기·확장자 제한은 storage 정책이라 이 Port 뒤에서 판단된다.
    F가 실제 S3 어댑터를 낼 때까지 로컬 stub으로 개발을 진행한다(F 병목 회피).

    연결된 클래스
    - IssueAttachmentUploadUrlService     : 업로드 URL 발급 호출
    - DeleteAttachmentService             : 오브젝트 삭제 호출
    - ProjectAttachmentStorageStubAdapter : 임시 구현체 (infrastructure.storage)
    - ProjectAttachment                   : Port가 다루는 파일의 메타데이터 모델
*/
public interface ProjectAttachmentStoragePort {
}
