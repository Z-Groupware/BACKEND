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
 * 클래스 레벨 @Transactional을 의도적으로 안 붙인다. Spring Data JPA 리포지토리는 기본적으로
 * 프록시 자체가 파생 쿼리 메서드에도 자체 트랜잭션을 감싸므로(enableDefaultTransactions,
 * 기본값 true — 이 프로젝트는 그 값을 끈 적 없음, application.yaml/설정 어디에도
 * enableDefaultTransactions=false가 없다), 호출자 쪽에 트랜잭션이 전혀 없어도 예외가 나면 안 된다.
 * 실제로 RecordingPartPersistenceAdapter.deleteByMeetingId도 동일 패턴(무-@Transactional)으로
 * 같은 비-트랜잭션 어댑터(RecordingAssemblyS3FfmpegAdapter)에서 이미 문제없이 쓰이고 있다.
 */
@SpringBootTest
@DisplayName("capture_upload_state 삭제 — 비-트랜잭션 호출 컨텍스트 재현(CodeRabbit 지적 검증)")
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
     * recordingPartRepository.deleteByMeetingId도 부른다(우리가 새로 추가한 캡처 상태 삭제
     * 바로 위 줄) — 같은 문제가 이미 있던 코드에도 있는지 같이 확인한다.
     */
    @Test
    @DisplayName("[대조군] recording_part 삭제도 트랜잭션 없이 부르면 마찬가지로 실패하는지 확인한다")
    void recordingPartDeleteAlsoRequiresTransaction() {
        recordingPartRepository.save(
                RecordingPart.create(700L, 0, 1, "stt-temp/org-1/meeting-700/segments/0/parts/0001.webm",
                        "audio/webm", 1_000L, 7L));

        assertThatCode(() -> recordingPartRepository.deleteByMeetingId(700L))
                .as("RecordingAssemblyS3FfmpegAdapter가 실제로 호출하는 것과 동일한 무-트랜잭션 조건")
                .doesNotThrowAnyException();
    }

    /*
     * 저장도, 삭제도 전부 테스트 메서드 바깥에 아무 트랜잭션 없이(클래스 레벨 @Transactional
     * 없음) 그대로 호출한다 — RecordingAssemblyS3FfmpegAdapter.startAssembly()가 부르는 상황과
     * 동일하다. TransactionRequiredException 없이 정상 삭제되면 CodeRabbit 지적은 이 코드베이스의
     * 실제 설정(기본 Spring Data 트랜잭션)에서는 해당하지 않는 것으로 확인된다.
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
