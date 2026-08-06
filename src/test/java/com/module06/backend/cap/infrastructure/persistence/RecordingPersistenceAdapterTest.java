package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;

/*
 * CAP-10 recording 테이블 저장·중복 확인 어댑터가 recording 매핑(컬럼·auto-increment)과 existsByMeetingId
 * 파생 쿼리를 실제 JPA로 올바르게 수행하는지 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("CAP-10 recording 영속성 어댑터")
class RecordingPersistenceAdapterTest {

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private SpringDataCapRecordingRepository springDataCapRecordingRepository;

    @BeforeEach
    void clear() {
        springDataCapRecordingRepository.deleteAll();
    }

    /* 저장하면 id가 생성되고 필드가 그대로 매핑되며, 해당 회의의 존재 확인이 true가 되는지 검증한다. */
    @Test
    @DisplayName("녹음본을 저장하고 회의별 존재를 확인한다")
    void savesAndChecksExistence() {
        Recording saved = recordingRepository.save(
                Recording.register(500L, "recording.ogg", "recordings/org-1/meeting-500/recording.ogg", 15_000_000L));

        // auto-increment id 부여 + 필드 매핑 확인.
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMeetingId()).isEqualTo(500L);
        assertThat(saved.getFileName()).isEqualTo("recording.ogg");
        assertThat(saved.getFileUrl()).isEqualTo("recordings/org-1/meeting-500/recording.ogg");
        assertThat(saved.getSizeBytes()).isEqualTo(15_000_000L);
        assertThat(saved.getDurationSec()).isNull();

        // 같은 회의는 존재, 다른 회의는 미존재.
        assertThat(recordingRepository.existsByMeetingId(500L)).isTrue();
        assertThat(recordingRepository.existsByMeetingId(999L)).isFalse();

        // 회의로 조회하면 저장한 녹음본이, 없는 회의는 empty.
        assertThat(recordingRepository.findByMeetingId(500L)).isPresent()
                .get().satisfies(r -> assertThat(r.getFileUrl()).isEqualTo("recordings/org-1/meeting-500/recording.ogg"));
        assertThat(recordingRepository.findByMeetingId(999L)).isEmpty();
    }

    /* 같은 회의로 두 번 저장하면 UNIQUE(meeting_id) 제약이 두 번째를 CAP-014로 막는지 검증한다(경쟁 최종 방어선). */
    @Test
    @DisplayName("같은 회의 중복 저장은 CAP-014로 막힌다")
    void rejectsDuplicateByUniqueConstraint() {
        recordingRepository.save(
                Recording.register(500L, "recording.ogg", "recordings/org-1/meeting-500/recording.ogg", 100L));

        assertThatThrownBy(() -> recordingRepository.save(
                Recording.register(500L, "recording.ogg", "recordings/org-1/meeting-500/recording.ogg", 200L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode().getCode())
                .isEqualTo("CAP-014");
    }
}
