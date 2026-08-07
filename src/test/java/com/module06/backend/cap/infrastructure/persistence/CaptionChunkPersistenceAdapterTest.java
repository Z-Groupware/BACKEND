package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.model.CaptionChunk;
import com.module06.backend.cap.domain.repository.CaptionChunkRepository;

/*
 * CAP-11 caption_chunk 저장 어댑터가 실제 JPA로 배치를 저장하고, UNIQUE(meeting_id, member_id, seq)
 * 위반(재전송)을 예외 없이 조용히 건너뛰는지 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("CAP-11 caption_chunk 영속성 어댑터")
class CaptionChunkPersistenceAdapterTest {

    @Autowired
    private CaptionChunkRepository captionChunkRepository;

    @Autowired
    private SpringDataCapCaptionChunkRepository springDataCapCaptionChunkRepository;

    @BeforeEach
    void clear() {
        springDataCapCaptionChunkRepository.deleteAll();
    }

    /* 새 배치는 전부 저장되고 필드가 그대로 매핑되는지 검증한다. */
    @Test
    @DisplayName("새 배치를 전부 저장한다")
    void savesNewBatch() {
        List<CaptionChunk> saved = captionChunkRepository.saveAllSkippingDuplicates(List.of(
                CaptionChunk.receive(500L, 7L, 41, 623_400, 625_100, "이거 제가 할게요", new BigDecimal("-12.40")),
                CaptionChunk.receive(500L, 7L, 42, 625_100, 626_000, "네 알겠습니다", new BigDecimal("-8.10"))));

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getId()).isNotNull();
        assertThat(saved.get(0).getText()).isEqualTo("이거 제가 할게요");
        assertThat(saved.get(0).getRms()).isEqualByComparingTo("-12.40");
        assertThat(springDataCapCaptionChunkRepository.count()).isEqualTo(2);
    }

    /* 이미 저장된 (meetingId, memberId, seq)는 재전송으로 보고 조용히 건너뛰고, 새 조각만 반환하는지 검증한다. */
    @Test
    @DisplayName("중복(meetingId, memberId, seq)은 건너뛰고 새 조각만 반환한다")
    void skipsDuplicatesInBatch() {
        captionChunkRepository.saveAllSkippingDuplicates(List.of(
                CaptionChunk.receive(500L, 7L, 41, 623_400, 625_100, "이거 제가 할게요", new BigDecimal("-12.40"))));

        // 재전송(seq=41 중복) + 새 조각(seq=42) 섞인 배치.
        List<CaptionChunk> saved = captionChunkRepository.saveAllSkippingDuplicates(List.of(
                CaptionChunk.receive(500L, 7L, 41, 623_400, 625_100, "이거 제가 할게요", new BigDecimal("-12.40")),
                CaptionChunk.receive(500L, 7L, 42, 625_100, 626_000, "네 알겠습니다", new BigDecimal("-8.10"))));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSeq()).isEqualTo(42);
        assertThat(springDataCapCaptionChunkRepository.count()).isEqualTo(2);
    }

    /* 다른 참석자(memberId)는 같은 seq를 써도 중복이 아닌지 검증한다(멱등 키가 참석자별로 독립). */
    @Test
    @DisplayName("다른 참석자는 같은 seq를 써도 중복이 아니다")
    void differentMemberSameSeqIsNotDuplicate() {
        List<CaptionChunk> saved = captionChunkRepository.saveAllSkippingDuplicates(List.of(
                CaptionChunk.receive(500L, 7L, 1, 0, 500, "첫 발화", new BigDecimal("-10.00")),
                CaptionChunk.receive(500L, 9L, 1, 0, 500, "다른 사람 첫 발화", new BigDecimal("-9.00"))));

        assertThat(saved).hasSize(2);
    }
}
