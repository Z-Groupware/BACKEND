package com.module06.backend.cap.application.usecase;

import com.module06.backend.cap.application.command.IssuePartUploadUrlsCommand;

import java.util.List;

// 컨트롤러가 부르는 "명찰" — 실제 구현체(CaptureUploadService)를 몰라도 되게 해준다.
public interface IssuePartUploadUrlsUseCase {

    // 회의당 청크 업로드용 presigned URL을 count개 발급한다
    Result issuePartUploadUrls(IssuePartUploadUrlsCommand command);

    // 발급 결과 — 현재 세그먼트 번호 + 발급된 URL 목록
    record Result(int segmentSeq, List<Part> parts) {
    }

    // URL 하나(청크 순번 + 서명된 URL + 만료초)
    record Part(int seq, String presignedUrl, int expiresInSeconds) {
    }
}
