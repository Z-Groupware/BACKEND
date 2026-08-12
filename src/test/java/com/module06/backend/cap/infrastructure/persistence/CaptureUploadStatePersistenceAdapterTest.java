package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;

/*
 * capture_upload_state 삭제 어댑터가 실제 JPA로 올바르게 동작하는지 검증한다. 최종 리뷰에서
 * CrudRepository.deleteById(meetingId)가 행이 없으면 EmptyResultDataAccessException을 던지는
 * 것을 발견 — CAP-10(수동 업로드)은 애초에 이 행이 없고, CAP-05 자동 조립은 이미 이 행을 지운
 * 뒤라, DeleteRecordingService(CAP-15)가 뒤이어 부르면 두 경로 다 삭제 전체가 500으로 실패했다.
 * 파생 삭제 쿼리(deleteByMeetingId)로 바꿔 0건이어도 조용히 넘어가게 고쳤다.
 */
@SpringBootTest
@Transactional
@DisplayName("capture_upload_state 삭제 영속성 어댑터")
class CaptureUploadStatePersistenceAdapterTest {

    @Autowired
    private CaptureUploadStateRepository captureUploadStateRepository;

    @Autowired
    private SpringDataCaptureUploadStateRepository springDataCaptureUploadStateRepository;

    @BeforeEach
    void clear() {
        springDataCaptureUploadStateRepository.deleteAll();
    }

    /* 존재하는 상태 행을 삭제하면 실제로 지워지는지 검증한다. */
    @Test
    @DisplayName("존재하는 상태 행을 삭제한다")
    void deletesExistingState() {
        captureUploadStateRepository.save(CaptureUploadState.startWithRecorder(500L, 7L));
        assertThat(captureUploadStateRepository.findByMeetingId(500L)).isPresent();

        captureUploadStateRepository.deleteByMeetingId(500L);

        assertThat(captureUploadStateRepository.findByMeetingId(500L)).isEmpty();
    }

    /*
     * 상태 행이 애초에 없는 회의(CAP-10 수동 업로드는 presign을 안 타 이 행이 없다)를 지워도
     * 예외 없이 조용히 넘어가는지 검증한다 — deleteById였다면 여기서 EmptyResultDataAccessException.
     */
    @Test
    @DisplayName("상태 행이 없는 회의를 지워도 예외가 나지 않는다")
    void deletingMissingStateDoesNotThrow() {
        assertThat(captureUploadStateRepository.findByMeetingId(999L)).isEmpty();

        assertThatCode(() -> captureUploadStateRepository.deleteByMeetingId(999L)).doesNotThrowAnyException();
    }

    /*
     * 조립·삭제가 같은 회의에 대해 연달아(조립이 먼저 지운 뒤 삭제가 다시) 호출되는 실제
     * 시나리오를 재현한다 — 두 번째 삭제도 예외 없이 넘어가야 한다.
     */
    @Test
    @DisplayName("이미 지워진 회의를 다시 지워도 예외가 나지 않는다(조립 후 삭제 순서 재현)")
    void deletingAlreadyDeletedStateDoesNotThrow() {
        captureUploadStateRepository.save(CaptureUploadState.startWithRecorder(500L, 7L));
        captureUploadStateRepository.deleteByMeetingId(500L);

        assertThatCode(() -> captureUploadStateRepository.deleteByMeetingId(500L)).doesNotThrowAnyException();
    }
}
