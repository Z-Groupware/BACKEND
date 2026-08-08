package com.module06.backend.cap.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.cap.domain.repository.ProcessingCompletionRepository;

/*
 * CAP-15 삭제 차단 판정(hasUnfinishedProcessing)이 stt_block·analysis_layer 실제 행에 대해
 * 파생 쿼리(existsByMeetingIdAndStatusNot/In/existsByMeetingId)로 올바르게 동작하는지 검증한다.
 *
 * stt_block·analysis_layer는 이태연(capture) 소유 테이블이라 cap은 읽기 전용
 * @Immutable 참조 엔티티만 갖고 있다(쓰기 엔티티 없음). 그래서 테스트 데이터는
 * JdbcTemplate으로 직접 insert한다 — 프로덕션에 쓰기 경로를 새로 만들지 않기 위함이다.
 */
@SpringBootTest
@Transactional
@DisplayName("CAP-15 STT·분석 완료 판정 어댑터")
class ProcessingCompletionRepositoryAdapterTest {

    @Autowired
    private ProcessingCompletionRepository processingCompletionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clear() {
        jdbcTemplate.update("DELETE FROM stt_block");
        jdbcTemplate.update("DELETE FROM analysis_layer");
    }

    /* STT가 애초에 트리거된 적 없으면(sttTriggered=false) 행이 없어도 완료로 보는지 검증한다. */
    @Test
    @DisplayName("STT 대상이 아니면 행이 없어도 완료로 본다")
    void treatsNoRowsAsFinishedWhenSttNotTriggered() {
        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L, false)).isFalse();
    }

    /* STT가 트리거됐는데(sttTriggered=true) 아직 블록이 0건이면 "진행 중"으로 보고 미완료 처리하는지 검증한다.
       (CodeRabbit 리뷰로 드러난 갭 — 등록 직후~첫 블록 생성 전 창구에서 confirm 없이 삭제되는 것을 막는다.) */
    @Test
    @DisplayName("STT가 트리거됐는데 블록이 0건이면 미완료다")
    void unfinishedWhenSttTriggeredButNoBlocksYet() {
        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L, true)).isTrue();
    }

    /* DONE이 아닌 STT 블록이 하나라도 있으면 미완료인지 검증한다. */
    @Test
    @DisplayName("DONE 아닌 STT 블록이 있으면 미완료다")
    void unfinishedWhenSttBlockNotDone() {
        insertSttBlock(500L, "RUNNING");

        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L, true)).isTrue();
    }

    /* STT 블록이 모두 DONE이고 분석 계층이 없으면 완료인지 검증한다. */
    @Test
    @DisplayName("STT가 모두 DONE이면 완료다")
    void finishedWhenAllSttBlocksDone() {
        insertSttBlock(500L, "DONE");
        insertSttBlock(500L, "DONE");

        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L, true)).isFalse();
    }

    /* PENDING/RUNNING/FAILED 분석 계층이 있으면 미완료인지 검증한다. */
    @Test
    @DisplayName("미완료 분석 계층(PENDING/RUNNING/FAILED)이 있으면 미완료다")
    void unfinishedWhenAnalysisLayerNotSettled() {
        insertSttBlock(500L, "DONE");
        insertAnalysisLayer(500L, "L2", "RUNNING");

        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L, true)).isTrue();
    }

    /* 분석 계층이 DONE·SKIPPED뿐이면 완료로 보는지 검증한다(SKIPPED도 종결 상태). */
    @Test
    @DisplayName("분석 계층이 DONE·SKIPPED뿐이면 완료다")
    void finishedWhenAnalysisLayersSettled() {
        insertSttBlock(500L, "DONE");
        insertAnalysisLayer(500L, "L1", "DONE");
        insertAnalysisLayer(500L, "L2", "SKIPPED");

        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L, true)).isFalse();
    }

    /* 다른 회의의 미완료 행은 이 회의 판정에 영향을 주지 않는지 검증한다. */
    @Test
    @DisplayName("다른 회의의 미완료 행은 영향을 주지 않는다")
    void ignoresOtherMeetings() {
        insertSttBlock(999L, "RUNNING");
        insertAnalysisLayer(999L, "L2", "RUNNING");

        // 500L 자체는 STT 대상이 아니므로(sttTriggered=false) 0건이 완료로 읽힌다.
        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L, false)).isFalse();
    }

    // capture 쪽에 쓰기 엔티티(SttBlockJpaEntity · STT-03·04)가 붙으면서 H2 test 스키마가 실제
    // 스키마(V5.4)와 같은 NOT NULL 컬럼들을 갖게 됐다 — 예전에는 CapSttBlockReferenceEntity가
    // 매핑한 셋(id/meeting_id/status)만 존재해서 두 컬럼만 채워도 들어갔다.
    //
    // 아래 값들은 이 테스트가 보지 않는다(판정은 status만 본다). 실제 스키마에서도 NOT NULL이라
    // 그때 안 채우면 어차피 못 넣는 값들이고, analysis_layer를 이미 같은 이유로 채우고 있다.
    private void insertSttBlock(Long meetingId, String status) {
        jdbcTemplate.update(
                "INSERT INTO stt_block "
                        + "(meeting_id, block_seq, start_offset_ms, end_offset_ms, cut_reason, "
                        + " provider, status, retry_count) "
                        + "VALUES (?, ?, 0, 0, 'VAD_SILENCE', 'aws-transcribe', ?, 0)",
                meetingId, nextBlockSeq++, status);
    }

    /* UNIQUE(meeting_id, block_seq) 라 같은 회의에 여러 건을 넣는 테스트가 순번을 나눠 써야 한다. */
    private int nextBlockSeq = 0;

    // attempt_count/tokens_in/tokens_out은 Flyway 마이그레이션엔 DEFAULT 0이 있지만, H2 test 스키마는
    // AnalysisLayerJpaEntity(create-drop)로 생성돼 기본값 없이 NOT NULL이라 명시적으로 채운다.
    private void insertAnalysisLayer(Long meetingId, String layer, String status) {
        jdbcTemplate.update(
                "INSERT INTO analysis_layer (meeting_id, layer, status, attempt_count, tokens_in, tokens_out) "
                        + "VALUES (?, ?, ?, 0, 0, 0)",
                meetingId, layer, status);
    }
}
