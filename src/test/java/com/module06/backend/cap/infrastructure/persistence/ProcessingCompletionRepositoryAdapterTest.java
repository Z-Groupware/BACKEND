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
 * 파생 쿼리(existsByMeetingIdAndStatusNot/In)로 올바르게 동작하는지 검증한다.
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

    /* 두 테이블 모두 행이 없으면(STT 시작 전) 미완료로 보지 않는지 검증한다. */
    @Test
    @DisplayName("아무 행도 없으면 완료로 본다")
    void treatsNoRowsAsFinished() {
        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L)).isFalse();
    }

    /* DONE이 아닌 STT 블록이 하나라도 있으면 미완료인지 검증한다. */
    @Test
    @DisplayName("DONE 아닌 STT 블록이 있으면 미완료다")
    void unfinishedWhenSttBlockNotDone() {
        insertSttBlock(500L, "RUNNING");

        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L)).isTrue();
    }

    /* STT 블록이 모두 DONE이고 분석 계층이 없으면 완료인지 검증한다. */
    @Test
    @DisplayName("STT가 모두 DONE이면 완료다")
    void finishedWhenAllSttBlocksDone() {
        insertSttBlock(500L, "DONE");
        insertSttBlock(500L, "DONE");

        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L)).isFalse();
    }

    /* PENDING/RUNNING/FAILED 분석 계층이 있으면 미완료인지 검증한다. */
    @Test
    @DisplayName("미완료 분석 계층(PENDING/RUNNING/FAILED)이 있으면 미완료다")
    void unfinishedWhenAnalysisLayerNotSettled() {
        insertSttBlock(500L, "DONE");
        insertAnalysisLayer(500L, "L2", "RUNNING");

        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L)).isTrue();
    }

    /* 분석 계층이 DONE·SKIPPED뿐이면 완료로 보는지 검증한다(SKIPPED도 종결 상태). */
    @Test
    @DisplayName("분석 계층이 DONE·SKIPPED뿐이면 완료다")
    void finishedWhenAnalysisLayersSettled() {
        insertSttBlock(500L, "DONE");
        insertAnalysisLayer(500L, "L1", "DONE");
        insertAnalysisLayer(500L, "L2", "SKIPPED");

        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L)).isFalse();
    }

    /* 다른 회의의 미완료 행은 이 회의 판정에 영향을 주지 않는지 검증한다. */
    @Test
    @DisplayName("다른 회의의 미완료 행은 영향을 주지 않는다")
    void ignoresOtherMeetings() {
        insertSttBlock(999L, "RUNNING");
        insertAnalysisLayer(999L, "L2", "RUNNING");

        assertThat(processingCompletionRepository.hasUnfinishedProcessing(500L)).isFalse();
    }

    // H2 test 스키마는 create-drop이라 CapSttBlockReferenceEntity가 매핑한 컬럼(id/meeting_id/status)만
    // 존재한다(Flyway 전체 DDL은 MySQL 전용이라 test 프로파일에 안 돈다) — 그 두 컬럼만 채운다.
    private void insertSttBlock(Long meetingId, String status) {
        jdbcTemplate.update(
                "INSERT INTO stt_block (meeting_id, status) VALUES (?, ?)",
                meetingId, status);
    }

    // attempt_count/tokens_in/tokens_out은 Flyway 마이그레이션엔 DEFAULT 0이 있지만, H2 test 스키마는
    // AnalysisLayerJpaEntity(create-drop)로 생성돼 기본값 없이 NOT NULL이라 명시적으로 채운다.
    private void insertAnalysisLayer(Long meetingId, String layer, String status) {
        jdbcTemplate.update(
                "INSERT INTO analysis_layer (meeting_id, layer, status, attempt_count, tokens_in, tokens_out) "
                        + "VALUES (?, ?, ?, 0, 0, 0)",
                meetingId, layer, status);
    }
}
