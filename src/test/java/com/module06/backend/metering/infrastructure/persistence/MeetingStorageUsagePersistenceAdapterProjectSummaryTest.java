package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.MeetingStorageUsage;
import com.module06.backend.metering.domain.model.ProjectStorageSummary;
import com.module06.backend.metering.domain.repository.MeetingStorageUsageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 저장소 관리 화면(/manage/storage)의 프로젝트별 음성 사용량 집계(summarizeByProjectId) 실 SQL·
 * Java 집계 정합성 테스트다. meetingCount·lastRecordedAt이 "지금 녹음이 남아있는 회의"만
 * 세는지(usedBytes=0인 삭제된 회의를 제외하는지)가 이 클래스가 검증하는 핵심이다.
 *
 * ⚠️ 회사·프로젝트·회의 id를 테스트 메서드마다 겹치지 않게 쓴다 — report()가 부르는 실제 쓰기
 * 경로(MeetingStorageUsageWriter)는 REQUIRES_NEW라 이 클래스의 @Transactional 롤백을 타지 않고
 * 그대로 커밋된다. 같은 id를 여러 메서드가 재사용하면 이전 메서드가 쓴 값이 남아 있는 채로
 * 다음 메서드가 돌아 합계가 어긋난다.
 */
@SpringBootTest
@Transactional
class MeetingStorageUsagePersistenceAdapterProjectSummaryTest {

    @Autowired
    private MeetingStorageUsageRepository meetingStorageUsageRepository;

    @Autowired
    private SpringDataMeetingStorageUsageRepository springDataMeetingStorageUsageRepository;

    @Test
    void groupsByProjectAndSumsBytes() {
        Long company = 5_010_001L;
        Long projectA = 6_010_001L;
        Long projectB = 6_010_002L;
        report(7_010_001L, company, projectA, 1_000L, LocalDateTime.of(2026, 8, 1, 0, 0));
        report(7_010_002L, company, projectA, 2_000L, LocalDateTime.of(2026, 8, 2, 0, 0));
        report(7_010_003L, company, projectB, 5_000L, LocalDateTime.of(2026, 8, 3, 0, 0));

        List<ProjectStorageSummary> result = meetingStorageUsageRepository.summarizeByProjectId(company);

        Optional<ProjectStorageSummary> summaryA = findByProject(result, projectA);
        assertThat(summaryA).isPresent();
        assertThat(summaryA.get().usedBytes()).isEqualTo(3_000L);
        assertThat(summaryA.get().meetingCount()).isEqualTo(2L);

        Optional<ProjectStorageSummary> summaryB = findByProject(result, projectB);
        assertThat(summaryB).isPresent();
        assertThat(summaryB.get().usedBytes()).isEqualTo(5_000L);
        assertThat(summaryB.get().meetingCount()).isEqualTo(1L);
    }

    @Test
    void excludesDeletedRecordingsFromMeetingCountButKeepsZeroInBytes() {
        Long company = 5_020_001L;
        Long projectA = 6_020_001L;
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime deletedAt = LocalDateTime.of(2026, 8, 5, 0, 0);
        report(7_020_001L, company, projectA, 1_000L, createdAt);
        // 삭제 — usedBytes=0으로 다시 report(DeleteRecordingService와 동일 패턴). row는 남는다.
        report(7_020_001L, company, projectA, 0L, deletedAt);

        List<ProjectStorageSummary> result = meetingStorageUsageRepository.summarizeByProjectId(company);

        Optional<ProjectStorageSummary> summaryA = findByProject(result, projectA);
        assertThat(summaryA).isPresent();
        // usedBytes 총합은 0(삭제됐으니) — 그런데 meetingCount는 "지금 남아있는" 회의만 세므로 0이어야 한다.
        assertThat(summaryA.get().usedBytes()).isZero();
        assertThat(summaryA.get().meetingCount()).isZero();
    }

    @Test
    void lastRecordedAtIgnoresDeletedRecordings() {
        Long company = 5_030_001L;
        Long projectA = 6_030_001L;
        report(7_030_001L, company, projectA, 1_000L, LocalDateTime.of(2026, 8, 1, 0, 0));
        report(7_030_002L, company, projectA, 2_000L, LocalDateTime.of(2026, 8, 10, 0, 0));
        // 더 최근에 삭제된(updatedAt이 가장 늦은) 회의 하나 — lastRecordedAt이 이 삭제 시각을
        // "마지막 녹음"으로 잘못 집지 않아야 한다.
        report(7_030_003L, company, projectA, 3_000L, LocalDateTime.of(2026, 8, 3, 0, 0));
        report(7_030_003L, company, projectA, 0L, LocalDateTime.of(2026, 8, 20, 0, 0));

        List<ProjectStorageSummary> result = meetingStorageUsageRepository.summarizeByProjectId(company);

        Optional<ProjectStorageSummary> summaryA = findByProject(result, projectA);
        assertThat(summaryA).isPresent();
        assertThat(summaryA.get().lastRecordedAt()).isEqualTo(LocalDateTime.of(2026, 8, 10, 0, 0));
    }

    @Test
    void excludesOtherCompanyRows() {
        Long company = 5_040_001L;
        Long otherCompany = 5_040_002L;
        Long projectA = 6_040_001L;
        report(7_040_001L, company, projectA, 1_000L, LocalDateTime.of(2026, 8, 1, 0, 0));
        report(7_040_002L, otherCompany, projectA, 999_999L, LocalDateTime.of(2026, 8, 1, 0, 0));

        List<ProjectStorageSummary> result = meetingStorageUsageRepository.summarizeByProjectId(company);

        Optional<ProjectStorageSummary> summaryA = findByProject(result, projectA);
        assertThat(summaryA).isPresent();
        assertThat(summaryA.get().usedBytes()).isEqualTo(1_000L);
        assertThat(summaryA.get().meetingCount()).isEqualTo(1L);
    }

    @Test
    void clearByCompanyIdAndProjectIdZerosOnlyMatchingProjectRowsAndBumpsRevision() {
        Long company = 5_050_001L;
        Long otherCompany = 5_050_002L;
        Long projectA = 6_050_001L;
        Long projectB = 6_050_002L;
        LocalDateTime reportedAt = LocalDateTime.of(2026, 8, 1, 0, 0);
        long revision = reportedAt.toEpochSecond(ZoneOffset.UTC);
        report(7_050_001L, company, projectA, 1_000L, reportedAt);
        report(7_050_002L, company, projectB, 2_000L, reportedAt);
        report(7_050_003L, otherCompany, projectA, 3_000L, reportedAt);

        meetingStorageUsageRepository.clearByCompanyIdAndProjectId(company, projectA);

        MeetingStorageUsage cleared = springDataMeetingStorageUsageRepository.findById(7_050_001L)
                .orElseThrow()
                .toDomain();
        MeetingStorageUsage otherProject = springDataMeetingStorageUsageRepository.findById(7_050_002L)
                .orElseThrow()
                .toDomain();
        MeetingStorageUsage otherCompanyRow = springDataMeetingStorageUsageRepository.findById(7_050_003L)
                .orElseThrow()
                .toDomain();
        assertThat(cleared.getUsedBytes()).isZero();
        assertThat(cleared.getRevision()).isEqualTo(revision + 1);
        assertThat(otherProject.getUsedBytes()).isEqualTo(2_000L);
        assertThat(otherProject.getRevision()).isEqualTo(revision);
        assertThat(otherCompanyRow.getUsedBytes()).isEqualTo(3_000L);
        assertThat(otherCompanyRow.getRevision()).isEqualTo(revision);
    }

    private void report(Long meetingId, Long companyId, Long projectId, long usedBytes, LocalDateTime updatedAt) {
        meetingStorageUsageRepository.reportIfNewer(
                MeetingStorageUsage.report(meetingId, companyId, projectId, usedBytes,
                        updatedAt.toEpochSecond(ZoneOffset.UTC), updatedAt));
    }

    private Optional<ProjectStorageSummary> findByProject(List<ProjectStorageSummary> result, Long projectId) {
        return result.stream().filter(summary -> summary.projectId().equals(projectId)).findFirst();
    }
}
