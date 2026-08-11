package com.module06.backend.project.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;
import com.module06.backend.project.domain.repository.ProjectRepository;

import static org.assertj.core.api.Assertions.assertThat;

/* comment.
    2026-08-10 목록 필터/정렬(이홍근 요청) 실 SQL 정합성 테스트. Specification으로 만든 동적
    조건이라 가짜 리포지터리로는 검증이 안 된다 — 실제 DB 위에서 status 필터와 dueDate 정렬이
    맞게 동작하는지, totalElements가 필터 적용 후 기준인지 확인한다.
*/
@SpringBootTest
@Transactional
class ProjectPersistenceAdapterListFilterTest {

    private static final Long COMPANY = 1L;
    private static final Long OWNER = 5L;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void filtersByStatusAndCountsOnlyMatchingRows() {
        save("TODO1", ProjectStatus.TODO, LocalDate.of(2026, 12, 31));
        save("PROG1", ProjectStatus.IN_PROGRESS, LocalDate.of(2026, 12, 31));
        save("PROG2", ProjectStatus.IN_PROGRESS, LocalDate.of(2026, 11, 30));

        List<Project> result = projectRepository.findAllByCompanyId(
                COMPANY, ProjectStatus.IN_PROGRESS, null, "desc", 0, 20);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(p -> p.getStatus() == ProjectStatus.IN_PROGRESS);
        assertThat(projectRepository.countByCompanyId(COMPANY, ProjectStatus.IN_PROGRESS)).isEqualTo(2L);
        // 필터 없이는 3건 전부 — totalElements가 필터 적용 전/후로 다르다는 것 자체를 확인한다.
        assertThat(projectRepository.countByCompanyId(COMPANY, null)).isEqualTo(3L);
    }

    @Test
    void sortsByDueDateAscending() {
        save("C", ProjectStatus.TODO, LocalDate.of(2026, 12, 31));
        save("A", ProjectStatus.TODO, LocalDate.of(2026, 1, 1));
        save("B", ProjectStatus.TODO, LocalDate.of(2026, 6, 15));

        List<Project> result = projectRepository.findAllByCompanyId(COMPANY, null, "dueDate", "asc", 0, 20);

        assertThat(result).extracting(Project::getDueDate).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 31));
    }

    // ---------- countDueSoonByCompanyId (이슈 #352, 오너 대시보드 "마감 D-7") ----------

    @Test
    void countDueSoonExcludesDoneProjectsAndProjectsOutsideWindow() {
        LocalDate today = LocalDate.now();
        save("SOON1", ProjectStatus.IN_PROGRESS, today.plusDays(3)); // 창 안, 카운트됨
        save("SOON2", ProjectStatus.TODO, today.plusDays(7)); // 창 경계(포함), 카운트됨
        save("DONE", ProjectStatus.DONE, today.plusDays(3)); // 완료라 제외
        save("LATER", ProjectStatus.TODO, today.plusDays(8)); // 창 밖이라 제외
        save("PAST", ProjectStatus.TODO, today.minusDays(1)); // 이미 지나서 제외

        long result = projectRepository.countDueSoonByCompanyId(COMPANY, today, today.plusDays(7));

        assertThat(result).isEqualTo(2L);
    }

    private void save(String tag, ProjectStatus status, LocalDate dueDate) {
        Project project = Project.create(
                COMPANY, tag, "프로젝트 " + tag, "설명", "#059669",
                LocalDate.of(2026, 1, 1), dueDate, OWNER, List.of());
        if (status != ProjectStatus.TODO) {
            project.changeStatus(status);
        }
        projectRepository.save(project);
    }
}
