package com.module06.backend.cap.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.usecase.CompletePartUploadUseCase;
import com.module06.backend.cap.application.usecase.GetPartUploadStatusUseCase;
import com.module06.backend.cap.application.usecase.IssuePartUploadUrlsUseCase;
import com.module06.backend.cap.presentation.api.dto.response.PartUploadStatusResponse;
import com.module06.backend.global.response.ApiResponse;

/*
 * CAP-08 청크 업로드 상태 조회 Controller가 인증 principal의 memberId를 유스케이스에 넘기고
 * 200 공통 응답으로 변환하는지 검증하는 단위 테스트다.
 */
@DisplayName("CAP-08 청크 업로드 상태 조회 Controller")
class CaptureUploadControllerTest {

    /* 상태 조회가 200 응답에 재개 정보 필드로 매핑되는지 검증한다. */
    @Test
    @DisplayName("청크 업로드 상태를 200 공통 응답으로 반환한다")
    void returnsPartUploadStatus() {
        /* 유스케이스에 전달된 인자를 기록할 공간을 준비한다. */
        Long[] captured = new Long[2];

        /* 인자를 기록하고 재개 정보를 반환하는 상태 조회 유스케이스 대역을 만든다. */
        GetPartUploadStatusUseCase statusUseCase = (meetingId, callerId) -> {
            captured[0] = meetingId;
            captured[1] = callerId;
            return new GetPartUploadStatusUseCase.Result(0, 84, List.of(), 4, 85, 0L);
        };
        CaptureUploadController controller = new CaptureUploadController(noopIssue(), noopComplete(), statusUseCase);

        /* 회의 500, 인증 principal 7번으로 상태를 조회한다. */
        ApiResponse<PartUploadStatusResponse> response = controller.status(500L, 7L);

        /* 회의 ID와 조회자 memberId는 principal 값 그대로 유스케이스에 전달돼야 한다. */
        assertThat(captured[0]).isEqualTo(500L);
        assertThat(captured[1]).isEqualTo(7L);

        /* 공통 200 상태와 조회 성공 메시지, 재개 정보 필드가 응답에 담겨야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("조회 성공");
        assertThat(response.getData()).isNotNull().satisfies(data -> {
            assertThat(data.segmentSeq()).isZero();
            assertThat(data.lastSeq()).isEqualTo(84);
            assertThat(data.missingSeqs()).isEmpty();
            assertThat(data.blocksFormed()).isEqualTo(4);
            assertThat(data.resumeFromSeq()).isEqualTo(85);
            assertThat(data.gapMs()).isZero();
        });
    }

    // status() 테스트에서는 호출되지 않는 발급 유스케이스 무동작 대역.
    private IssuePartUploadUrlsUseCase noopIssue() {
        return command -> {
            throw new AssertionError("status 테스트에서 presign 유스케이스는 호출되면 안 됩니다.");
        };
    }

    // status() 테스트에서는 호출되지 않는 완료 통보 유스케이스 무동작 대역.
    private CompletePartUploadUseCase noopComplete() {
        return command -> {
            throw new AssertionError("status 테스트에서 complete 유스케이스는 호출되면 안 됩니다.");
        };
    }
}
