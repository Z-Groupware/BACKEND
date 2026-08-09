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
 * CAP-11 caption_chunk 저장 어댑터가 실제 JPA로 배치를 저장하고 UNIQUE(meeting_id, member_id, seq)
 * 위반(재전송)을 예외 없이 조용히 건너뛰는지, CAP-12 조회 어댑터가 시간순으로 돌려주는지 검증한다.
 */
@SpringBootTest
@Transactional
@DisplayName("CAP-11·12 caption_chunk 영속성 어댑터")
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

    /* 같은 배치 안에 (meetingId, memberId, seq)가 중복돼도(클라이언트 버그 등) UNIQUE 위반으로 배치 전체가
       죽지 않고, 하나만 저장되는지 검증한다(DB엔 아직 없어 사전 존재 확인만으로는 못 걸러지는 경우). */
    @Test
    @DisplayName("같은 배치 내부 중복은 UNIQUE 위반 없이 하나만 저장한다")
    void dedupesWithinSameBatch() {
        List<CaptionChunk> saved = captionChunkRepository.saveAllSkippingDuplicates(List.of(
                CaptionChunk.receive(500L, 7L, 41, 623_400, 625_100, "이거 제가 할게요", new BigDecimal("-12.40")),
                CaptionChunk.receive(500L, 7L, 41, 623_400, 625_100, "이거 제가 할게요", new BigDecimal("-12.40"))));

        assertThat(saved).hasSize(1);
        assertThat(springDataCapCaptionChunkRepository.count()).isEqualTo(1);
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

    /* 저장 순서와 무관하게 발화 시작 오프셋(startOffsetMs) 순으로 조회되는지 검증한다(CAP-12 백필용). */
    @Test
    @DisplayName("자막 전체를 시간순으로 조회한다")
    void findsAllOrderedByStartOffset() {
        captionChunkRepository.saveAllSkippingDuplicates(List.of(
                CaptionChunk.receive(500L, 7L, 2, 10_000, 10_500, "두 번째", new BigDecimal("-10.00")),
                CaptionChunk.receive(500L, 7L, 1, 5_000, 5_500, "첫 번째", new BigDecimal("-9.00"))));
        captionChunkRepository.saveAllSkippingDuplicates(List.of(
                CaptionChunk.receive(999L, 7L, 1, 0, 500, "다른 회의", new BigDecimal("-9.00"))));

        List<CaptionChunk> found = captionChunkRepository.findByMeetingId(500L);

        assertThat(found).hasSize(2);
        assertThat(found.get(0).getText()).isEqualTo("첫 번째");
        assertThat(found.get(1).getText()).isEqualTo("두 번째");
    }
}
