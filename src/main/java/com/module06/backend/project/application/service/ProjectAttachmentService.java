package com.module06.backend.project.application.service;

import com.module06.backend.project.application.usecase.ConfirmAttachmentUseCase;
import com.module06.backend.project.application.usecase.DeleteAttachmentUseCase;
import com.module06.backend.project.application.usecase.IssueAttachmentUploadUrlUseCase;

/* comment.
    프로젝트 첨부파일 리소스(FR-PJ-08)를 다루는 단일 구현체. 셋 다 쓰기 트랜잭션이다.
    흐름: issueUploadUrl로 presigned URL 발급 → 클라이언트 업로드 → confirm으로 메타데이터 확정,
    삭제는 업로더 본인/LEADER+만 가능.
    UseCase 인터페이스는 엔드포인트 1:1(포트 경계)로 유지하되, 구현체는 같은 애그리거트를
    다루는 것끼리 이 클래스 하나로 묶었다 — 08/04 팀 협의(윤종호)로 서비스 클래스 파편화를 줄이는 쪽으로 확정.

    연결된 클래스
    - IssueAttachmentUploadUrlUseCase · ConfirmAttachmentUseCase · DeleteAttachmentUseCase : 구현하는 계약
    - ProjectAttachmentStoragePort : storage(F) 도메인 경계 호출
    - ProjectAttachmentRepository  : 저장·조회
*/
public class ProjectAttachmentService implements
        IssueAttachmentUploadUrlUseCase,
        ConfirmAttachmentUseCase,
        DeleteAttachmentUseCase {
}
