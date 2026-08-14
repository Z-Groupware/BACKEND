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
                COMPANY, null, ProjectStatus.IN_PROGRESS, null, "desc", 0, 20);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(p -> p.getStatus() == ProjectStatus.IN_PROGRESS);
        assertThat(projectRepository.countByCompanyId(COMPANY, null, ProjectStatus.IN_PROGRESS)).isEqualTo(2L);
        // 필터 없이는 3건 전부 — totalElements가 필터 적용 전/후로 다르다는 것 자체를 확인한다.
        assertThat(projectRepository.countByCompanyId(COMPANY, null, null)).isEqualTo(3L);
    }

    @Test
    void sortsByDueDateAscending() {
        save("C", ProjectStatus.TODO, LocalDate.of(2026, 12, 31));
        save("A", ProjectStatus.TODO, LocalDate.of(2026, 1, 1));
        save("B", ProjectStatus.TODO, LocalDate.of(2026, 6, 15));

        List<Project> result = projectRepository.findAllByCompanyId(COMPANY, null, null, "dueDate", "asc", 0, 20);

        assertThat(result).extracting(Project::getDueDate).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 31));
    }

    @Test
    void sortsByNameAscending() {
        save("Charlie", ProjectStatus.TODO, LocalDate.of(2026, 12, 31));
        save("Alpha", ProjectStatus.TODO, LocalDate.of(2026, 1, 1));
        save("Bravo", ProjectStatus.TODO, LocalDate.of(2026, 6, 15));

        List<Project> result = projectRepository.findAllByCompanyId(COMPANY, null, null, "name", "asc", 0, 20);

        // save() 헬퍼가 name을 "프로젝트 " + tag로 조립한다 — 접두어가 모든 행에 동일하므로 tag 순서가 곧 name 순서다.
        assertThat(result).extracting(Project::getName).containsExactly("프로젝트 Alpha", "프로젝트 Bravo", "프로젝트 Charlie");
    }

    @Test
    void filtersByKeywordCaseInsensitive() {
        save("Zebra Groupware", ProjectStatus.TODO, LocalDate.of(2026, 12, 31));
        save("zebra internal tools", ProjectStatus.TODO, LocalDate.of(2026, 6, 15));
        save("Other Project", ProjectStatus.TODO, LocalDate.of(2026, 1, 1));

        List<Project> result = projectRepository.findAllByCompanyId(COMPANY, "ZEBRA", null, null, "desc", 0, 20);

        assertThat(result).hasSize(2);
        // CodeRabbit 지적(PR #452) — 개수·count만 보면 조건이 틀려도 우연히 2건이 나오면 통과한다.
        // 실제로 어떤 프로젝트가 반환됐는지까지 확인한다.
        assertThat(result).extracting(Project::getName)
                .containsExactlyInAnyOrder("프로젝트 Zebra Groupware", "프로젝트 zebra internal tools");
        assertThat(projectRepository.countByCompanyId(COMPANY, "ZEBRA", null)).isEqualTo(2L);
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

    // startDate는 null로 둔다 — status는 이제 startDate로부터 파생되므로(이슈 #497),
    // 고정된 과거 날짜를 넣으면 TODO로 남아야 할 행이 조회 시점에 IN_PROGRESS로 재계산되어
    // 이 파일의 status 필터/정렬 검증과 어긋난다. 상태 변경이 필요한 행은 changeStatus로 명시한다.
    private void save(String tag, ProjectStatus status, LocalDate dueDate) {
        Project project = Project.create(
                COMPANY, tag, "프로젝트 " + tag, "설명", "#059669",
                null, dueDate, OWNER, List.of());
        if (status != ProjectStatus.TODO) {
            project.changeStatus(status, LocalDate.now());
        }
        projectRepository.save(project);
    }
}
