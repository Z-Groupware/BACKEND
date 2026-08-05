package com.module06.backend.cap.infrastructure.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.module06.backend.cap.application.port.out.CapObjectStoragePort;

/* comment.
    CapObjectStoragePort 임시 구현체(project의 ProjectAttachmentStorageStubAdapter와 동일 패턴).
    본인(김현지)이 실제 S3 어댑터를 낼 때까지 개발용으로 쓴다 — @Profile("!prod")로 운영에서만
    확실히 안 뜨게 막는다. 실제 파일 업로드 없음: 가짜 URL 문자열만 돌려준다.
*/
@Component
@Profile("!prod")
public class CapObjectStorageStubAdapter implements CapObjectStoragePort {

    private static final int EXPIRES_IN_SECONDS = 900; // 15분 — 문서(CAP-04) 예시값과 동일

    // 진짜 S3를 안 부르고 가짜 URL 문자열만 만들어서 돌려줌
    @Override
    public IssuedPartUploadUrl issuePartUploadUrl(String s3Key, String contentType) {
        return new IssuedPartUploadUrl("https://stub-storage.local/upload/" + s3Key, EXPIRES_IN_SECONDS);
    }
}
