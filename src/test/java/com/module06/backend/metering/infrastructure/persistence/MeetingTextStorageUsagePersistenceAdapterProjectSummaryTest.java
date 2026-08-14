package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.TextStorageSource;
import com.module06.backend.metering.domain.repository.MeetingTextStorageUsageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 저장소 관리 화면(/manage/storage)의 프로젝트별 자막·요약 사용량 집계(sumUsedBytesGroupedByProjectId)
 * 실 SQL·Java 집계 정합성 테스트다. 세 소스(캡션·transcript·요약)가 같은 회의에 각자 리포트해도
 * 프로젝트 합계에는 세 소스가 다 더해지는지가 핵심이다.
 *
 * ⚠️ 회사·프로젝트·회의 id를 테스트 메서드마다 겹치지 않게 쓴다 — 이유는
 * MeetingStorageUsagePersistenceAdapterProjectSummaryTest 클래스 주석과 동일(REQUIRES_NEW 쓰기는
 * 이 클래스의 @Transactional 롤백을 타지 않는다).
 */
@SpringBootTest
@Transactional
class MeetingTextStorageUsagePersistenceAdapterProjectSummaryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 0, 0);

    @Autowired
    private MeetingTextStorageUsageRepository meetingTextStorageUsageRepository;

    @Autowired
    private SpringDataMeetingTextStorageUsageRepository springDataMeetingTextStorageUsageRepository;

    @Test
    void sumsAllThreeSourcesPerMeetingAndGroupsByProject() {
        Long company = 5_110_001L;
        Long projectA = 6_110_001L;
        Long meetingId = 8_010_001L;
        report(meetingId, company, projectA, TextStorageSource.CAPTION, 1_000L, 1L);
        report(meetingId, company, projectA, TextStorageSource.TRANSCRIPT, 2_000L, 2L);
        report(meetingId, company, projectA, TextStorageSource.SUMMARY, 3_000L, 3L);

        Map<Long, Long> result = meetingTextStorageUsageRepository.sumUsedBytesGroupedByProjectId(company);

        // 한 회의의 세 소스(1000+2000+3000)가 다 더해져 프로젝트 합계에 들어가야 한다 — 이게 이
        // 클래스를 별도 컬럼으로 다시 설계한 이유(Commit 3 재작업)를 그대로 검증한다.
        assertThat(result.get(projectA)).isEqualTo(6_000L);
    }

    @Test
    void groupsMultipleMeetingsByProject() {
        Long company = 5_120_001L;
        Long projectA = 6_120_001L;
        Long projectB = 6_120_002L;
        report(8_020_001L, company, projectA, TextStorageSource.CAPTION, 1_000L, 10L);
        report(8_020_002L, company, projectA, TextStorageSource.CAPTION, 500L, 11L);
        report(8_020_003L, company, projectB, TextStorageSource.CAPTION, 9_000L, 12L);

        Map<Long, Long> result = meetingTextStorageUsageRepository.sumUsedBytesGroupedByProjectId(company);

        assertThat(result.get(projectA)).isEqualTo(1_500L);
        assertThat(result.get(projectB)).isEqualTo(9_000L);
    }

    @Test
    void excludesOtherCompanyRows() {
        Long company = 5_130_001L;
        Long otherCompany = 5_130_002L;
        Long projectA = 6_130_001L;
        report(8_030_001L, company, projectA, TextStorageSource.CAPTION, 1_000L, 20L);
        report(8_030_002L, otherCompany, projectA, TextStorageSource.CAPTION, 999_999L, 21L);

        Map<Long, Long> result = meetingTextStorageUsageRepository.sumUsedBytesGroupedByProjectId(company);

        assertThat(result.get(projectA)).isEqualTo(1_000L);
    }

    @Test
    void clearByCompanyIdAndProjectIdZerosAllSourceColumnsOnlyForMatchingProjectAndBumpsRevisions() {
        Long company = 5_140_001L;
        Long otherCompany = 5_140_002L;
        Long projectA = 6_140_001L;
        Long projectB = 6_140_002L;
        report(8_040_001L, company, projectA, TextStorageSource.CAPTION, 1_000L, 1L);
        report(8_040_001L, company, projectA, TextStorageSource.TRANSCRIPT, 2_000L, 2L);
        report(8_040_001L, company, projectA, TextStorageSource.SUMMARY, 3_000L, 3L);
        report(8_040_002L, company, projectB, TextStorageSource.CAPTION, 4_000L, 4L);
        report(8_040_003L, otherCompany, projectA, TextStorageSource.CAPTION, 5_000L, 5L);

        meetingTextStorageUsageRepository.clearByCompanyIdAndProjectId(company, projectA);

        MeetingTextStorageUsageJpaEntity clearedEntity = springDataMeetingTextStorageUsageRepository
                .findById(8_040_001L)
                .orElseThrow();
        MeetingTextStorageUsageJpaEntity otherProjectEntity = springDataMeetingTextStorageUsageRepository
                .findById(8_040_002L)
                .orElseThrow();
        MeetingTextStorageUsageJpaEntity otherCompanyEntity = springDataMeetingTextStorageUsageRepository
                .findById(8_040_003L)
                .orElseThrow();

        var cleared = clearedEntity.toDomain();
        var otherProject = otherProjectEntity.toDomain();
        var otherCompanyRow = otherCompanyEntity.toDomain();
        assertThat(cleared.getCaptionBytes()).isZero();
        assertThat(cleared.getTranscriptBytes()).isZero();
        assertThat(cleared.getSummaryBytes()).isZero();
        assertThat(cleared.getCaptionRevision()).isEqualTo(2L);
        assertThat(cleared.getTranscriptRevision()).isEqualTo(3L);
        assertThat(cleared.getSummaryRevision()).isEqualTo(4L);
        assertThat(otherProject.getCaptionBytes()).isEqualTo(4_000L);
        assertThat(otherProject.getCaptionRevision()).isEqualTo(4L);
        assertThat(otherCompanyRow.getCaptionBytes()).isEqualTo(5_000L);
        assertThat(otherCompanyRow.getCaptionRevision()).isEqualTo(5L);
    }

    private void report(Long meetingId, Long companyId, Long projectId, TextStorageSource source, long usedBytes,
                        long revision) {
        meetingTextStorageUsageRepository.reportIfNewer(meetingId, companyId, projectId, source, usedBytes,
                revision, NOW);
    }
}
