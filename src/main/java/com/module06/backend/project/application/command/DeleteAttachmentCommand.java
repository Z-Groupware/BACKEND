package com.module06.backend.project.application.command;

/* comment.
    첨부파일 삭제 명령. 첨부파일 id와 요청자 id를 담는다.
    프로젝트 자체의 삭제(D)는 스코프 아웃이지만, 첨부파일 삭제는 스코프 안이다.
    메타데이터 삭제와 오브젝트 삭제 두 곳을 모두 정리해야 고아 파일이 남지 않는다.

    연결된 클래스
    - DeleteAttachmentUseCase       : 이 명령을 받는 기능 계약
    - DeleteAttachmentService       : 이 명령을 처리하는 구현체
    - ProjectAttachmentStoragePort  : 오브젝트 삭제를 위임하는 경계
*/
public record DeleteAttachmentCommand() {
}
