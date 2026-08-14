package com.module06.backend.meeting.application.port.out;

/* MEET-18이 S3에 먼저 업로드된 녹음 객체를 CAP의 recording으로 확정하는 경계다. */
public interface OnlineMeetingRecordingPort {

    /* 최종 요청의 S3 객체를 검증하고 이후 회의 트랜잭션 롤백 시 보상 삭제를 예약한다. */
    default void prepare(Preparation preparation) {
    }

    void register(Registration registration);

    record Preparation(
            Long companyId,
            Long hostMemberId,
            String s3Key,
            String fileName,
            String contentType,
            Long sizeBytes
    ) {
    }

    record Registration(
            Long companyId,
            Long hostMemberId,
            Long projectId,
            Long meetingId,
            String s3Key,
            String fileName,
            String contentType,
            Long sizeBytes
    ) {
    }
}
