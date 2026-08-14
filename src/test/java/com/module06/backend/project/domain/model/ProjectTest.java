package com.module06.backend.project.domain.model;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Project.status 파생 — 이슈 #497.
 *
 * <p>DONE 은 사람이 직접 지정할 때만 세팅되는 유일한 값이라 그대로 신뢰하고, 그 외에는
 * startDate 와 오늘 날짜를 비교해 매번 다시 계산한다(Action 의 isDone/startDate mirror
 * 패턴과 같은 발상이나, project 테이블엔 is_done 컬럼이 없어 저장된 status==DONE 자체를
 * 신호로 재사용한다). ProjectStatus.java 의 "전환 제약 없음, 단계 건너뛰기·되돌리기 모두
 * 허용" 정책은 그대로 유지 — Action 처럼 start()/complete()/reopen() 가드를 걸지 않는다.
 */
class ProjectTest {

    private static final Long OWNER = 1L;
    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate PAST = TODAY.minusDays(5);
    private static final LocalDate FUTURE = TODAY.plusDays(5);

    private Project reconstitute(ProjectStatus storedStatus, LocalDate startDate) {
        return Project.reconstitute(1L, 1L, "TAG", "이름", "설명", "#16A34A",
                storedStatus, startDate, LocalDate.of(2026, 12, 31), OWNER, List.of(), null, null, null);
    }

    @Test
    @DisplayName("재조회 시 startDate 가 과거면 저장된 status 가 TODO 라도 IN_PROGRESS 로 재계산된다")
    void 시작일이_지나면_자동으로_진행중이_된다() {
        Project project = reconstitute(ProjectStatus.TODO, PAST);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("재조회 시 startDate 가 미래면 TODO 를 유지한다")
    void 시작일_전이면_할일을_유지한다() {
        Project project = reconstitute(ProjectStatus.TODO, FUTURE);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.TODO);
    }

    @Test
    @DisplayName("startDate 가 null 이면 TODO 다")
    void 시작일이_없으면_할일이다() {
        Project project = reconstitute(ProjectStatus.TODO, null);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.TODO);
    }

    @Test
    @DisplayName("저장된 status 가 DONE 이면 startDate 와 무관하게 DONE 을 유지한다 — 완료는 재계산 대상이 아니다")
    void 완료는_시작일과_무관하게_유지된다() {
        Project project = reconstitute(ProjectStatus.DONE, FUTURE);

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DONE);
    }

    @Test
    @DisplayName("changeStatus(IN_PROGRESS) — startDate 가 없으면 오늘로 채운다(당겨서 일찍 시작)")
    void 진행중으로_바꾸면_시작일이_없을_때_오늘로_채운다() {
        Project project = reconstitute(ProjectStatus.TODO, null);

        project.changeStatus(ProjectStatus.IN_PROGRESS, TODAY);

        assertThat(project.getStartDate()).isEqualTo(TODAY);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("changeStatus(IN_PROGRESS) — startDate 가 미래면 오늘로 당긴다")
    void 진행중으로_바꾸면_미래_시작일을_오늘로_당긴다() {
        Project project = reconstitute(ProjectStatus.TODO, FUTURE);

        project.changeStatus(ProjectStatus.IN_PROGRESS, TODAY);

        assertThat(project.getStartDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("changeStatus(IN_PROGRESS) — startDate 가 이미 과거면 그대로 둔다")
    void 진행중으로_바꿔도_이미_지난_시작일은_안_건드린다() {
        Project project = reconstitute(ProjectStatus.TODO, PAST);

        project.changeStatus(ProjectStatus.IN_PROGRESS, TODAY);

        assertThat(project.getStartDate()).isEqualTo(PAST);
    }

    @Test
    @DisplayName("changeStatus(TODO) — startDate 를 비워 되돌린다(정책상 되돌리기 허용)")
    void 할일로_되돌리면_시작일을_비운다() {
        Project project = reconstitute(ProjectStatus.IN_PROGRESS, PAST);

        project.changeStatus(ProjectStatus.TODO, TODAY);

        assertThat(project.getStartDate()).isNull();
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.TODO);
    }

    @Test
    @DisplayName("changeStatus(DONE) — startDate 는 건드리지 않는다")
    void 완료로_바꿔도_시작일은_그대로_둔다() {
        Project project = reconstitute(ProjectStatus.TODO, FUTURE);

        project.changeStatus(ProjectStatus.DONE, TODAY);

        assertThat(project.getStartDate()).isEqualTo(FUTURE);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DONE);
    }

    @Test
    @DisplayName("changeStatus(TODO) 후에도 재조회하면 여전히 TODO 다 — null startDate 는 미래로 취급하지 않는다")
    void 할일로_되돌린_뒤_재조회해도_할일이다() {
        Project project = reconstitute(ProjectStatus.IN_PROGRESS, PAST);
        project.changeStatus(ProjectStatus.TODO, TODAY);

        Project reloaded = reconstitute(project.getStatus(), project.getStartDate());

        assertThat(reloaded.getStatus()).isEqualTo(ProjectStatus.TODO);
    }

    @Test
    @DisplayName("생성 시점에 startDate 가 과거면 응답에 즉시 IN_PROGRESS 로 반영된다 — 다음 조회를 기다리지 않는다")
    void 생성_직후_응답도_시작일이_지났으면_바로_진행중이다() {
        Project project = Project.create(1L, "TAG", "이름", "설명", "#16A34A",
                LocalDate.now().minusDays(1), LocalDate.of(2099, 12, 31), OWNER, List.of());

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("수정으로 startDate 를 과거로 옮기면 응답에도 즉시 반영된다")
    void 수정_직후_응답도_시작일이_지났으면_바로_진행중이다() {
        Project project = reconstitute(ProjectStatus.TODO, FUTURE);

        project.update("새이름", "설명", "#16A34A", LocalDate.now().minusDays(1), LocalDate.of(2099, 12, 31), List.of());

        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }
}
