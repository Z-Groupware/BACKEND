package com.module06.backend.handover.infrastructure.persistence;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.handover.application.command.CreateHandoverCommand;
import com.module06.backend.handover.application.command.ReassignItemCommand;
import com.module06.backend.handover.application.command.RejectHandoverCommand;
import com.module06.backend.handover.application.service.HandoverService;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverItem;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;
import com.module06.backend.handover.domain.repository.HandoverRepository;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.auth.infrastructure.persistence.InMemoryRefreshTokenStore;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Handover domain integration")
@SpringBootTest
@Transactional
class HandoverDomainIntegrationTest {

    private static final Long COMPANY = 901L;
    private static final Long TEAM = 911L;
    private static final Long WRITER = 921L;
    private static final Long TARGET = 922L;
    private static final Long LEADER = 923L;
    private static final Long WRITER_POSITION = 931L;
    private static final Long TARGET_POSITION = 932L;
    private static final Long PROJECT = 941L;
    private static final Long DONE_PROJECT = 942L;
    private static final Long MEETING = 951L;
    private static final Long ACTION_TODO = 961L;
    private static final Long ACTION_DONE = 962L;
    private static final Long TEAM_ACTION = 963L;
    private static final LocalDateTime LEAVE_START = LocalDateTime.of(2026, 8, 17, 9, 0);
    private static final LocalDateTime LEAVE_END = LocalDateTime.of(2026, 8, 21, 18, 0);
    private static final LocalDate LAST_WORKING_DAY = LocalDate.of(2026, 8, 31);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 12, 0);

    @TestConfiguration
    static class InMemoryStoreConfig {
        @Bean
        @Primary
        RefreshTokenStore inMemoryRefreshTokenStore() {
            return new InMemoryRefreshTokenStore();
        }
    }

    @Autowired
    private HandoverService handoverService;

    @Autowired
    private HandoverRepository handoverRepository;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void setUpSecurityContext() {
        AuthPrincipal principal = new AuthPrincipal(LEADER, COMPANY, "LEADER", false, TEAM);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("create VACATION persists only selected handoverable action snapshots")
    void createVacationPersistsSelectedActions() {
        seedOrganization(false);
        insertProject(PROJECT, "P-HO", "Handover Project", "IN_PROGRESS");
        insertMeeting(MEETING, PROJECT, WRITER);
        insertPersonalAction(ACTION_TODO, PROJECT, MEETING, WRITER, "TODO", false, "Selected action");
        insertPersonalAction(ACTION_DONE, PROJECT, MEETING, WRITER, "DONE", true, "Unselected done action");

        Handover saved = handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.VACATION, LEAVE_START, LEAVE_END, null, "vacation", List.of(ACTION_TODO)
        ));

        assertThat(saved.getStatus()).isEqualTo(HandoverStatus.SUBMITTED);
        assertThat(saved.getTeamNameSnap()).isEqualTo("Platform");
        assertThat(statusOfMember(WRITER)).isEqualTo("WAITING");
        assertThat(saved.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getActionId()).isEqualTo(ACTION_TODO);
                    assertThat(item.getActionTitleSnap()).isEqualTo("Selected action");
                    assertThat(item.getProjectTagSnap()).isEqualTo("P-HO");
                    assertThat(item.getSourceMeetingTitleSnap()).isEqualTo("Project handover sync");
                    assertThat(item.isReassignRequired()).isTrue();
                });
        assertThat(itemCount(saved.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("create rejects VACATION selection when the action is not handoverable")
    void createVacationRejectsSelectedActionThatIsNotHandoverable() {
        seedOrganization(false);
        insertProject(PROJECT, "P-HO", "Handover Project", "IN_PROGRESS");
        insertProject(DONE_PROJECT, "P-DONE", "Closed Project", "DONE");
        insertMeeting(MEETING, PROJECT, WRITER);
        insertPersonalAction(ACTION_TODO, PROJECT, MEETING, WRITER, "TODO", false, "Available action");
        insertPersonalAction(ACTION_DONE, DONE_PROJECT, MEETING, WRITER, "TODO", false, "Closed project action");

        assertThatThrownBy(() -> handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.VACATION, LEAVE_START, LEAVE_END, null, null,
                List.of(ACTION_TODO, ACTION_DONE)
        )))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(HandoverErrorCode.HO_SELECTED_ACTION_NOT_HANDOVERABLE));
    }

    @Test
    @DisplayName("same writer cannot create a second active handover")
    void createRejectsSecondActiveHandoverForSameWriter() {
        seedOrganization(false);
        insertProject(PROJECT, "P-HO", "Handover Project", "IN_PROGRESS");
        insertMeeting(MEETING, PROJECT, WRITER);
        insertPersonalAction(ACTION_TODO, PROJECT, MEETING, WRITER, "TODO", false, "Active handover action");

        handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.VACATION, LEAVE_START, LEAVE_END, null, null, List.of(ACTION_TODO)
        ));

        assertThatThrownBy(() -> handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.VACATION, LEAVE_START.plusDays(7), LEAVE_END.plusDays(7), null, null,
                List.of(ACTION_TODO)
        )))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(HandoverErrorCode.HO_ACTIVE_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("reassignItem and complete reassign the action and persist committedAt")
    void reassignAndCompleteCommitsActionReassignment() {
        seedOrganization(false);
        insertProject(PROJECT, "P-HO", "Handover Project", "IN_PROGRESS");
        insertMeeting(MEETING, PROJECT, WRITER);
        insertPersonalAction(ACTION_TODO, PROJECT, MEETING, WRITER, "TODO", false, "Action to move");
        Handover handover = handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.VACATION, LEAVE_START, LEAVE_END, null, null, List.of(ACTION_TODO)
        ));

        handoverService.reassignItem(new ReassignItemCommand(handover.getId(), ACTION_TODO, TARGET, NOW));
        Handover completed = handoverService.complete(handover.getId(), LEADER, NOW.plusHours(1));

        assertThat(completed.getStatus()).isEqualTo(HandoverStatus.REASSIGNED);
        assertThat(assigneeOfAction(ACTION_TODO)).isEqualTo(TARGET);
        assertThat(storedCommittedAt(handover.getId(), ACTION_TODO)).isNotNull();
        Handover restored = handoverRepository.findById(handover.getId()).orElseThrow();
        assertThat(restored.getItems()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getReassigneeId()).isEqualTo(TARGET);
                    assertThat(item.getReassigneeNameSnap()).isEqualTo("Target");
                    assertThat(item.getReassigneePositionSnap()).isEqualTo("Staff");
                    assertThat(item.getCommittedAt()).isEqualTo(NOW.plusHours(1));
                });
    }

    @Test
    @DisplayName("handover item persistence restores committedAt, rollbackStatus, and reassignee snapshots")
    void handoverItemPersistenceRoundTripsCommitAndRollbackFields() {
        seedOrganization(false);
        HandoverItem item = new HandoverItem(
                null, ACTION_TODO, "Round trip", "TODO", "P-HO", "PERSONAL",
                LocalDate.of(2026, 8, 30), NOW.minusDays(1), MEETING, "Project handover sync", "content",
                TARGET, "Target", "Staff", NOW, NOW.plusHours(1), "ROLLED_BACK", true
        );
        Handover handover = Handover.createVacation(
                WRITER, TEAM, "Platform", "Writer", "Manager", "note", LEAVE_START, LEAVE_END, List.of(item)
        );

        Handover saved = handoverRepository.save(handover);
        Handover restored = handoverRepository.findById(saved.getId()).orElseThrow();

        assertThat(restored.getItems()).singleElement()
                .satisfies(restoredItem -> {
                    assertThat(restoredItem.getCommittedAt()).isEqualTo(NOW.plusHours(1));
                    assertThat(restoredItem.getRollbackStatus()).isEqualTo("ROLLED_BACK");
                    assertThat(restoredItem.getReassigneeId()).isEqualTo(TARGET);
                    assertThat(restoredItem.getReassigneeNameSnap()).isEqualTo("Target");
                    assertThat(restoredItem.getReassigneePositionSnap()).isEqualTo("Staff");
                });
    }

    @Test
    @DisplayName("finalize OFFBOARDING soft-deletes writer, vacates leader seat, revokes tokens, and creates insights")
    void finalizeOffboardingAppliesDepartureSideEffects() {
        seedOrganization(true);
        insertProject(PROJECT, "P-HO", "Handover Project", "IN_PROGRESS");
        insertMeeting(MEETING, PROJECT, WRITER);
        insertMeetingAttendee(MEETING, WRITER);
        insertMeetingAttendee(MEETING, TARGET);
        insertMeetingTopic(MEETING, "MAIN", "handover context", 0);
        insertPersonalAction(ACTION_TODO, PROJECT, MEETING, WRITER, "TODO", false, "Action to reassign");
        insertPersonalAction(ACTION_DONE, PROJECT, MEETING, WRITER, "DONE", true, "Audit action");
        insertTeamAction(TEAM_ACTION, PROJECT, MEETING, TEAM, "TODO", "Team action");
        refreshTokenStore.save(WRITER, "refresh-token", Duration.ofDays(14));
        Handover handover = handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.OFFBOARDING, null, null, LAST_WORKING_DAY, "offboarding", null
        ));
        assertThat(handover.getHandoverType()).isEqualTo(HandoverType.OFFBOARDING);
        assertThat(handover.getItems()).extracting(HandoverItem::getActionId)
                .containsExactlyInAnyOrder(ACTION_TODO, ACTION_DONE);

        handoverService.reassignItem(new ReassignItemCommand(handover.getId(), ACTION_TODO, TARGET, NOW));
        handoverService.complete(handover.getId(), LEADER, NOW.plusHours(1));
        Handover finalized = handoverService.finalize(handover.getId(), LEADER, "Leader", NOW.plusHours(2));

        assertThat(finalized.getStatus()).isEqualTo(HandoverStatus.FINALIZED);
        assertThat(statusOfMember(WRITER)).isEqualTo("RESIGNED");
        assertThat(deletedAtOfMember(WRITER)).isNotNull();
        assertThat(authorityOfMember(WRITER)).isEqualTo("MEMBER");
        assertThat(leaderMemberIdOfTeam(TEAM)).isNull();
        assertThat(refreshTokenStore.exists(WRITER, "refresh-token")).isFalse();
        assertThat(insightCount(handover.getId())).isGreaterThan(0);
    }

    @Disabled("C ActionReassignPort#rollbackReassignment 실구현 대기 — 머지되면 활성화")
    @Test
    @DisplayName("reject after complete rolls action assignment back to writer")
    void rejectAfterCompleteRollsBackCommittedReassignment() {
        seedOrganization(false);
        insertProject(PROJECT, "P-HO", "Handover Project", "IN_PROGRESS");
        insertMeeting(MEETING, PROJECT, WRITER);
        insertPersonalAction(ACTION_TODO, PROJECT, MEETING, WRITER, "TODO", false, "Rollback action");
        Handover handover = handoverService.create(new CreateHandoverCommand(
                WRITER, TEAM, HandoverType.VACATION, LEAVE_START, LEAVE_END, null, null, List.of(ACTION_TODO)
        ));
        handoverService.reassignItem(new ReassignItemCommand(handover.getId(), ACTION_TODO, TARGET, NOW));
        handoverService.complete(handover.getId(), LEADER, NOW.plusHours(1));

        Handover rejected = handoverService.reject(new RejectHandoverCommand(handover.getId(), "rollback"));

        assertThat(rejected.getStatus()).isEqualTo(HandoverStatus.REJECTED);
        assertThat(assigneeOfAction(ACTION_TODO)).isEqualTo(WRITER);
        assertThat(storedRollbackStatus(handover.getId(), ACTION_TODO)).isEqualTo("ROLLED_BACK");
        assertThat(statusOfMember(WRITER)).isEqualTo("ACTIVE");
    }

    private void seedOrganization(boolean writerIsLeader) {
        insertCompany(COMPANY);
        insertPosition(WRITER_POSITION, "Manager");
        insertPosition(TARGET_POSITION, "Staff");
        insertTeam(TEAM, "Platform", null);
        insertMember(WRITER, TEAM, WRITER_POSITION, "Writer", writerIsLeader ? "LEADER" : "MEMBER", "ACTIVE");
        insertMember(TARGET, TEAM, TARGET_POSITION, "Target", "MEMBER", "ACTIVE");
        insertMember(LEADER, TEAM, WRITER_POSITION, "Leader", "LEADER", "ACTIVE");
        if (writerIsLeader) {
            updateTeamLeader(TEAM, WRITER);
        } else {
            updateTeamLeader(TEAM, LEADER);
        }
        em.flush();
        em.clear();
    }

    private void insertCompany(Long id) {
        em.createNativeQuery("INSERT INTO company (id, code, name) VALUES (?, ?, ?)")
                .setParameter(1, id)
                .setParameter(2, "C" + id)
                .setParameter(3, "Test Company")
                .executeUpdate();
    }

    private void insertPosition(Long id, String name) {
        em.createNativeQuery("INSERT INTO position (id, company_id, name, authority, description) VALUES (?, ?, ?, ?, ?)")
                .setParameter(1, id)
                .setParameter(2, COMPANY)
                .setParameter(3, name)
                .setParameter(4, "MEMBER")
                .setParameter(5, name)
                .executeUpdate();
    }

    private void insertTeam(Long id, String name, Long leaderMemberId) {
        em.createNativeQuery("INSERT INTO team (id, company_id, name, leader_member_id) VALUES (?, ?, ?, ?)")
                .setParameter(1, id)
                .setParameter(2, COMPANY)
                .setParameter(3, name)
                .setParameter(4, leaderMemberId)
                .executeUpdate();
    }

    private void updateTeamLeader(Long teamId, Long leaderMemberId) {
        em.createNativeQuery("UPDATE team SET leader_member_id = ? WHERE id = ?")
                .setParameter(1, leaderMemberId)
                .setParameter(2, teamId)
                .executeUpdate();
    }

    private void insertMember(Long id, Long teamId, Long positionId, String name, String authority, String status) {
        em.createNativeQuery("MERGE INTO role (id, name) KEY(id) VALUES (2, 'member')")
                .executeUpdate();
        em.createNativeQuery("""
                        INSERT INTO member
                          (id, company_id, team_id, role_id, position_id, email, password_hash,
                           name, authority, is_admin, status, deleted_at)
                        VALUES (?, ?, ?, 2, ?, ?, 'hash', ?, ?, FALSE, ?, NULL)
                        """)
                .setParameter(1, id)
                .setParameter(2, COMPANY)
                .setParameter(3, teamId)
                .setParameter(4, positionId)
                .setParameter(5, "m" + id + "@example.test")
                .setParameter(6, name)
                .setParameter(7, authority)
                .setParameter(8, status)
                .executeUpdate();
    }

    private void insertProject(Long id, String tag, String name, String status) {
        em.createNativeQuery("""
                        INSERT INTO project (id, company_id, tag, name, color, status, due_date, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, '2026-08-01 09:00:00', '2026-08-01 09:00:00')
                        """)
                .setParameter(1, id)
                .setParameter(2, COMPANY)
                .setParameter(3, tag)
                .setParameter(4, name)
                .setParameter(5, "#6B7280")
                .setParameter(6, status)
                .setParameter(7, LocalDate.of(2026, 9, 30))
                .executeUpdate();
    }

    private void insertMeeting(Long id, Long projectId, Long hostMemberId) {
        em.createNativeQuery("""
                        INSERT INTO meeting
                          (id, company_id, project_id, team_id, meeting_room_id, host_member_id, title, status,
                           start_at, end_at, recording_consent, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 1, ?, 'Project handover sync', 'DONE',
                                '2026-08-01 10:00:00', '2026-08-01 11:00:00', TRUE,
                                '2026-08-01 09:00:00', '2026-08-01 09:00:00')
                        """)
                .setParameter(1, id)
                .setParameter(2, COMPANY)
                .setParameter(3, projectId)
                .setParameter(4, TEAM)
                .setParameter(5, hostMemberId)
                .executeUpdate();
    }

    private void insertMeetingAttendee(Long meetingId, Long memberId) {
        em.createNativeQuery("INSERT INTO meeting_attendee (meeting_id, member_id) VALUES (?, ?)")
                .setParameter(1, meetingId)
                .setParameter(2, memberId)
                .executeUpdate();
    }

    private void insertMeetingTopic(Long meetingId, String topicType, String content, int sortOrder) {
        em.createNativeQuery("""
                        INSERT INTO meeting_topic
                          (meeting_id, parent_topic_id, topic_type, content, sort_order, created_at, updated_at)
                        VALUES (?, NULL, ?, ?, ?, '2026-08-01 09:00:00', '2026-08-01 09:00:00')
                        """)
                .setParameter(1, meetingId)
                .setParameter(2, topicType)
                .setParameter(3, content)
                .setParameter(4, sortOrder)
                .executeUpdate();
    }

    private void insertPersonalAction(Long id, Long projectId, Long meetingId, Long assigneeMemberId,
                                      String status, boolean done, String title) {
        em.createNativeQuery("""
                        INSERT INTO action
                          (id, company_id, project_id, parent_action_id, source_meeting_id, team_id,
                           assignee_member_id, action_type, title, description, status, is_done, start_date,
                           due_date, due_date_defaulted, review_status, assignee_source, evidence_transcript_id,
                           gate_signals, is_manual, confirmed_at, created_at, updated_at)
                        VALUES (?, ?, ?, NULL, ?, NULL, ?, 'PERSONAL', ?, 'description', ?, ?, NULL,
                                '2026-08-30', FALSE, 'HUMAN_CONFIRMED', 'EXPLICIT_CALL', NULL,
                                NULL, TRUE, '2026-08-01 09:00:00', '2026-08-01 09:00:00', '2026-08-01 09:00:00')
                        """)
                .setParameter(1, id)
                .setParameter(2, COMPANY)
                .setParameter(3, projectId)
                .setParameter(4, meetingId)
                .setParameter(5, assigneeMemberId)
                .setParameter(6, title)
                .setParameter(7, status)
                .setParameter(8, done)
                .executeUpdate();
    }

    private void insertTeamAction(Long id, Long projectId, Long meetingId, Long teamId, String status, String title) {
        em.createNativeQuery("""
                        INSERT INTO action
                          (id, company_id, project_id, parent_action_id, source_meeting_id, team_id,
                           assignee_member_id, action_type, title, description, status, is_done, start_date,
                           due_date, due_date_defaulted, review_status, assignee_source, evidence_transcript_id,
                           gate_signals, is_manual, confirmed_at, created_at, updated_at)
                        VALUES (?, ?, ?, NULL, ?, ?, NULL, 'TEAM', ?, 'team description', ?, FALSE, NULL,
                                '2026-08-30', FALSE, 'HUMAN_CONFIRMED', NULL, NULL,
                                NULL, TRUE, '2026-08-01 09:00:00', '2026-08-01 09:00:00', '2026-08-01 09:00:00')
                        """)
                .setParameter(1, id)
                .setParameter(2, COMPANY)
                .setParameter(3, projectId)
                .setParameter(4, meetingId)
                .setParameter(5, teamId)
                .setParameter(6, title)
                .setParameter(7, status)
                .executeUpdate();
    }

    private String statusOfMember(Long memberId) {
        em.flush();
        em.clear();
        return (String) em.createNativeQuery("SELECT status FROM member WHERE id = ?")
                .setParameter(1, memberId)
                .getSingleResult();
    }

    private Object deletedAtOfMember(Long memberId) {
        em.flush();
        em.clear();
        return em.createNativeQuery("SELECT deleted_at FROM member WHERE id = ?")
                .setParameter(1, memberId)
                .getSingleResult();
    }

    private String authorityOfMember(Long memberId) {
        em.flush();
        em.clear();
        return (String) em.createNativeQuery("SELECT authority FROM member WHERE id = ?")
                .setParameter(1, memberId)
                .getSingleResult();
    }

    private Object leaderMemberIdOfTeam(Long teamId) {
        em.flush();
        em.clear();
        return em.createNativeQuery("SELECT leader_member_id FROM team WHERE id = ?")
                .setParameter(1, teamId)
                .getSingleResult();
    }

    private Long assigneeOfAction(Long actionId) {
        em.flush();
        em.clear();
        Number assignee = (Number) em.createNativeQuery("SELECT assignee_member_id FROM action WHERE id = ?")
                .setParameter(1, actionId)
                .getSingleResult();
        return assignee.longValue();
    }

    private Object storedCommittedAt(Long handoverId, Long actionId) {
        em.flush();
        em.clear();
        return em.createNativeQuery("SELECT committed_at FROM handover_item WHERE handover_id = ? AND action_id = ?")
                .setParameter(1, handoverId)
                .setParameter(2, actionId)
                .getSingleResult();
    }

    private String storedRollbackStatus(Long handoverId, Long actionId) {
        em.flush();
        em.clear();
        return (String) em.createNativeQuery("SELECT rollback_status FROM handover_item WHERE handover_id = ? AND action_id = ?")
                .setParameter(1, handoverId)
                .setParameter(2, actionId)
                .getSingleResult();
    }

    private int itemCount(Long handoverId) {
        Number count = (Number) em.createNativeQuery("SELECT COUNT(*) FROM handover_item WHERE handover_id = ?")
                .setParameter(1, handoverId)
                .getSingleResult();
        return count.intValue();
    }

    private int insightCount(Long handoverId) {
        Number count = (Number) em.createNativeQuery("SELECT COUNT(*) FROM handover_insight WHERE handover_id = ?")
                .setParameter(1, handoverId)
                .getSingleResult();
        return count.intValue();
    }
}
