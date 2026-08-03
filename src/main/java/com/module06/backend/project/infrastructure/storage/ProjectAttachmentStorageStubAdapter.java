package com.module06.backend.project.infrastructure.storage;

import com.module06.backend.project.application.port.ProjectAttachmentStoragePort;

/* comment.
    ProjectAttachmentStoragePort의 임시 구현체. storage(F, 김현지)가 실제 S3 어댑터를
    낼 때까지 C의 개발을 막지 않기 위한 스텁이다(F는 A 캡처·F 인프라·k6 삼중 부담이라 병목 위험).
    동작: 가짜 업로드 URL을 만들어 돌려주고, 삭제는 성공으로 처리한다. 실제 파일 이동은 없다.

    ⚠️ 이 클래스는 F의 구현이 들어오면 제거 대상이다. 로컬·테스트 프로파일에만 활성화하고
    운영 프로파일에 실려 나가지 않게 해야 한다 — 아니면 파일이 저장된 줄 알고 넘어간다.

    연결된 클래스
    - ProjectAttachmentStoragePort    : 구현하는 계약
    - IssueAttachmentUploadUrlService : 이 스텁을 호출하는 유스케이스
    - DeleteAttachmentService         : 이 스텁을 호출하는 유스케이스
*/
public class ProjectAttachmentStorageStubAdapter implements ProjectAttachmentStoragePort {
}
