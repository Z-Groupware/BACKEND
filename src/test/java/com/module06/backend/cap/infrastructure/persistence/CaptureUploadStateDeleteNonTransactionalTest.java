package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.CaptureUploadStateRepository;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;

/*
 * CodeRabbit이 CaptureUploadStatePersistenceAdapter.deleteByMeetingId에 @Transactional이 없어
 * TransactionRequiredException이 날 수 있다고 지적(RecordingAssemblyS3FfmpegAdapter가 비-트랜잭션
 * 컨텍스트에서 이 메서드를 부른다는 근거)했다 — 그 실제 호출 조건을 그대로 재현해 검증한다.
 *
 * 클래스 레벨 @Transactional을 의도적으로 안 붙인다 — RecordingAssemblyS3FfmpegAdapter가 실제로
 * 이 어댑터들을 부르는 조건(호출자 쪽에 트랜잭션이 전혀 없음)을 그대로 재현해야 하기 때문이다.
 *
 * 처음엔 "Spring Data가 파생 쿼리도 기본적으로 트랜잭션을 감싸준다"고 가정하고 이 테스트를
 * 작성했는데, 실제로 돌려보니 그 가정이 틀렸다(TransactionRequiredException 발생) — CodeRabbit
 * 지적이 맞았다. CaptureUploadStatePersistenceAdapter.deleteByMeetingId·
 * RecordingPartPersistenceAdapter.deleteByMeetingId 둘 다 어댑터 자체에 @Transactional을
 * 추가해서 고쳤고, 이 테스트는 이제 그 수정이 실제로 트랜잭션 없는 호출 조건에서도 동작하는지
 * 검증하는 회귀 테스트다.
 */
@SpringBootTest
@DisplayName("capture_upload_state·recording_part 삭제 — 비-트랜잭션 호출자 조건에서 어댑터의 @Transactional 검증")
class CaptureUploadStateDeleteNonTransactionalTest {

    @Autowired
    private CaptureUploadStateRepository captureUploadStateRepository;

    @Autowired
    private RecordingPartRepository recordingPartRepository;

    @AfterEach
    void clear() {
        captureUploadStateRepository.deleteByMeetingId(700L);
        recordingPartRepository.deleteByMeetingId(700L);
    }

    /*
     * RecordingAssemblyS3FfmpegAdapter.startAssembly()는 비-트랜잭션 컨텍스트에서
     * recordingPartRepository.deleteByMeetingId도 부른다(캡처 상태 삭제 바로 위 줄) — 같은
     * 조건에서 어댑터의 @Transactional이 실제로 동작하는지 대조군으로 같이 확인한다.
     */
    @Test
    @DisplayName("[대조군] recording_part 삭제도 호출자에 트랜잭션이 없어도 정상 삭제된다")
    void recordingPartDeleteSucceedsWithoutSurroundingTransaction() {
        recordingPartRepository.save(
                RecordingPart.create(700L, 0, 1, "stt-temp/org-1/meeting-700/segments/0/parts/0001.webm",
                        "audio/webm", 1_000L, 7L));
        assertThat(recordingPartRepository.findSeqsInSegment(700L, 0)).containsExactly(1);

        assertThatCode(() -> recordingPartRepository.deleteByMeetingId(700L))
                .as("RecordingAssemblyS3FfmpegAdapter가 실제로 호출하는 것과 동일한 무-트랜잭션 조건")
                .doesNotThrowAnyException();

        assertThat(recordingPartRepository.findSeqsInSegment(700L, 0)).isEmpty();
    }

    /*
     * 저장도, 삭제도 전부 테스트 메서드 바깥에 아무 트랜잭션 없이(클래스 레벨 @Transactional
     * 없음) 그대로 호출한다 — RecordingAssemblyS3FfmpegAdapter.startAssembly()가 부르는 상황과
     * 동일하다. 어댑터의 @Transactional 덕분에 TransactionRequiredException 없이 정상 삭제된다.
     */
    @Test
    @DisplayName("호출자에 트랜잭션이 전혀 없어도 예외 없이 삭제된다")
    void deletesWithoutAnySurroundingTransaction() {
        captureUploadStateRepository.save(CaptureUploadState.startWithRecorder(700L, 7L));
        assertThat(captureUploadStateRepository.findByMeetingId(700L)).isPresent();

        assertThatCode(() -> captureUploadStateRepository.deleteByMeetingId(700L)).doesNotThrowAnyException();

        assertThat(captureUploadStateRepository.findByMeetingId(700L)).isEmpty();
    }
}
