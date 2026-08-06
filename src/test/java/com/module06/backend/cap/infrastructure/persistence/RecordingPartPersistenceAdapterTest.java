package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;

/*
 * CAP-08 missingSeqs 계산의 근거가 되는 findSeqsInSegment 파생 쿼리(seq 닫힌 프로젝션)가
 * 회의·세그먼트 범위를 정확히 지키는지 실제 JPA로 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("CAP-08 recording_part 조회 영속성 어댑터")
class RecordingPartPersistenceAdapterTest {

    /* 애플리케이션 계층이 사용하는 실제 청크 저장소 계약이다. */
    @Autowired
    private RecordingPartRepository recordingPartRepository;

    /* 청크 행을 초기화하는 기술 저장소다. */
    @Autowired
    private SpringDataRecordingPartRepository springDataRecordingPartRepository;

    @BeforeEach
    void clear() {
        springDataRecordingPartRepository.deleteAll();
    }

    /* 요청한 회의·세그먼트의 순번만 반환하고 다른 세그먼트·회의는 제외되는지 검증한다. */
    @Test
    @DisplayName("현재 회의·세그먼트의 청크 순번만 반환한다")
    void returnsSeqsOnlyForGivenMeetingAndSegment() {
        /* 회의 500 세그먼트 0에 1·2번, 세그먼트 1에 1번, 다른 회의 999에 1번을 저장한다. */
        save(500L, 0, 1);
        save(500L, 0, 2);
        save(500L, 1, 1);
        save(999L, 0, 1);

        /* 회의 500 세그먼트 0으로 조회하면 그 범위의 1·2번만 반환돼야 한다. */
        assertThat(recordingPartRepository.findSeqsInSegment(500L, 0))
                .containsExactlyInAnyOrder(1, 2);

        /* 세그먼트 1은 1번만, 청크가 없는 세그먼트는 빈 목록이어야 한다. */
        assertThat(recordingPartRepository.findSeqsInSegment(500L, 1)).containsExactly(1);
        assertThat(recordingPartRepository.findSeqsInSegment(500L, 9)).isEmpty();
    }

    /* 완료 통보 경로와 동일하게 UPLOADED 청크 한 행을 저장한다. */
    private void save(Long meetingId, int segmentSeq, int seq) {
        String s3Key = "stt-temp/org-1/meeting-%d/segments/%d/parts/%04d.webm".formatted(meetingId, segmentSeq, seq);
        recordingPartRepository.save(
                RecordingPart.create(meetingId, segmentSeq, seq, s3Key, "audio/webm", 1_000L, 7L));
    }
}
