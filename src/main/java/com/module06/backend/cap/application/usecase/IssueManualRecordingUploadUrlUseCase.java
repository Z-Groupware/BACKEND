package com.module06.backend.cap.application.usecase;

import com.module06.backend.cap.application.command.IssueManualRecordingUploadUrlCommand;

// 컨트롤러가 부르는 "명찰" — 수동 녹음 업로드(CAP-10) 전 단계, presigned PUT URL 발급의
// 실제 구현체(ManualRecordingService)를 몰라도 되게 한다.
public interface IssueManualRecordingUploadUrlUseCase {

    // 이 회의의 영구 보관 경로(recordings/org-{orgId}/meeting-{meetingId}/{fileName})로
    // 업로드 가능한 presigned URL 하나를 발급한다.
    Result issueManualRecordingUploadUrl(IssueManualRecordingUploadUrlCommand command);

    /**
     * 발급 결과.
     *
     * @param s3Key 클라이언트가 업로드 후 CAP-10({@code POST .../recordings/manual})에 그대로
     *              돌려줘야 하는 키다 — registerManualRecording의 접두 검증과 반드시 같은 값이어야
     *              한다(여기서 만든 규칙과 거기서 검증하는 규칙이 어긋나면 정상 업로드도 거부된다).
     */
    record Result(String s3Key, String uploadUrl, int expiresInSeconds) {
    }
}
