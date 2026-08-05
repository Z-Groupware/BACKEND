package com.module06.backend.handover.presentation.api;

import com.module06.backend.handover.application.command.CreateHandoverCommand;
import com.module06.backend.handover.application.command.ReassignItemCommand;
import com.module06.backend.handover.application.command.RejectHandoverCommand;
import com.module06.backend.handover.application.port.out.OrgQueryPort;
import com.module06.backend.handover.application.usecase.CompleteHandoverUseCase;
import com.module06.backend.handover.application.usecase.CreateHandoverUseCase;
import com.module06.backend.handover.application.usecase.FinalizeHandoverUseCase;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase;
import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase;
import com.module06.backend.handover.application.usecase.HandoverToSuccessorUseCase;
import com.module06.backend.handover.application.usecase.ReassignHandoverItemUseCase;
import com.module06.backend.handover.application.usecase.RejectHandoverUseCase;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverItem;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HandoverController.class)
@AutoConfigureMockMvc(addFilters = false) // Pure mapping slice; auth wiring belongs to B(auth) integration tests.
class HandoverControllerTest {

    private static final Long HANDOVER_ID = 1000L;
    private static final Long WRITER = 1L;
    private static final Long TEAM = 10L;
    private static final Long ACTION = 100L;
    private static final Long TARGET = 2L;
    private static final Long APPROVER = 9L;
    private static final LocalDate START = LocalDate.of(2026, 8, 10);
    private static final LocalDate END = LocalDate.of(2026, 8, 20);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateHandoverUseCase createHandoverUseCase;

    @MockitoBean
    private ReassignHandoverItemUseCase reassignHandoverItemUseCase;

    @MockitoBean
    private CompleteHandoverUseCase completeHandoverUseCase;

    @MockitoBean
    private FinalizeHandoverUseCase finalizeHandoverUseCase;

    @MockitoBean
    private RejectHandoverUseCase rejectHandoverUseCase;

    @MockitoBean
    private GetHandoverListUseCase getHandoverListUseCase;

    @MockitoBean
    private GetHandoverPackageUseCase getHandoverPackageUseCase;

    @MockitoBean
    private HandoverToSuccessorUseCase handoverToSuccessorUseCase;

    @MockitoBean
    private OrgQueryPort orgQueryPort;

    @Test
    void createMapsRequestToCommandAndReturnsCreatedApiResponse() throws Exception {
        when(createHandoverUseCase.create(any(CreateHandoverCommand.class))).thenReturn(submitted());

        mockMvc.perform(post("/api/handovers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "writerMemberId": 1,
                                  "teamId": 10,
                                  "handoverType": "VACATION",
                                  "leaveStartAt": "2026-08-10",
                                  "leaveEndAt": "2026-08-20",
                                  "selectedActionIds": [100]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.httpStatus").value(201))
                .andExpect(jsonPath("$.data.id").value(HANDOVER_ID))
                .andExpect(jsonPath("$.data.items[0].actionId").value(ACTION));

        ArgumentCaptor<CreateHandoverCommand> captor = ArgumentCaptor.forClass(CreateHandoverCommand.class);
        verify(createHandoverUseCase).create(captor.capture());
        assertThat(captor.getValue().writerMemberId()).isEqualTo(WRITER);
        assertThat(captor.getValue().teamId()).isEqualTo(TEAM);
        assertThat(captor.getValue().handoverType()).isEqualTo(HandoverType.VACATION);
        assertThat(captor.getValue().selectedActionIds()).containsExactly(ACTION);
    }

    @Test
    void reassignMapsPathAndBodyToCommand() throws Exception {
        when(reassignHandoverItemUseCase.reassignItem(any(ReassignItemCommand.class))).thenReturn(submitted());

        mockMvc.perform(patch("/api/handovers/{id}/items/{actionId}/reassign", HANDOVER_ID, ACTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toMemberId": 2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value(200));

        ArgumentCaptor<ReassignItemCommand> captor = ArgumentCaptor.forClass(ReassignItemCommand.class);
        verify(reassignHandoverItemUseCase).reassignItem(captor.capture());
        assertThat(captor.getValue().handoverId()).isEqualTo(HANDOVER_ID);
        assertThat(captor.getValue().actionId()).isEqualTo(ACTION);
        assertThat(captor.getValue().toMemberId()).isEqualTo(TARGET);
        assertThat(captor.getValue().reassignedAt()).isNotNull();
    }

    @Test
    void completeUsesMemberIdHeaderAndCurrentTime() throws Exception {
        when(completeHandoverUseCase.complete(eq(HANDOVER_ID), eq(APPROVER), any(LocalDateTime.class)))
                .thenReturn(reassigned());

        mockMvc.perform(patch("/api/handovers/{id}/complete", HANDOVER_ID)
                        .header("X-Member-Id", APPROVER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REASSIGNED"));

        verify(completeHandoverUseCase).complete(eq(HANDOVER_ID), eq(APPROVER), any(LocalDateTime.class));
    }

    @Test
    void finalizeResolvesApproverNameFromOrgPort() throws Exception {
        when(orgQueryPort.findMember(APPROVER)).thenReturn(new OrgQueryPort.MemberSnapshot(APPROVER, "Park", "Leader"));
        when(finalizeHandoverUseCase.finalize(eq(HANDOVER_ID), eq(APPROVER), eq("Park"), any(LocalDateTime.class)))
                .thenReturn(finalized());

        mockMvc.perform(patch("/api/handovers/{id}/finalize", HANDOVER_ID)
                        .header("X-Member-Id", APPROVER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINALIZED"))
                .andExpect(jsonPath("$.data.finalApproverNameSnap").value("Park"));

        verify(orgQueryPort).findMember(APPROVER);
        verify(finalizeHandoverUseCase).finalize(eq(HANDOVER_ID), eq(APPROVER), eq("Park"), any(LocalDateTime.class));
    }

    @Test
    void rejectMapsReasonToCommand() throws Exception {
        when(rejectHandoverUseCase.reject(any(RejectHandoverCommand.class))).thenReturn(rejected());

        mockMvc.perform(patch("/api/handovers/{id}/reject", HANDOVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "needs more detail"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        ArgumentCaptor<RejectHandoverCommand> captor = ArgumentCaptor.forClass(RejectHandoverCommand.class);
        verify(rejectHandoverUseCase).reject(captor.capture());
        assertThat(captor.getValue().handoverId()).isEqualTo(HANDOVER_ID);
        assertThat(captor.getValue().reason()).isEqualTo("needs more detail");
    }

    @Test
    void listMapsQueryParamsAndReturnsSummaries() throws Exception {
        when(getHandoverListUseCase.list(any(GetHandoverListUseCase.HandoverListQuery.class)))
                .thenReturn(List.of(summary()));

        mockMvc.perform(get("/api/handovers").param("teamId", TEAM.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(HANDOVER_ID))
                .andExpect(jsonPath("$.data[0].itemCount").value(1));

        ArgumentCaptor<GetHandoverListUseCase.HandoverListQuery> captor =
                ArgumentCaptor.forClass(GetHandoverListUseCase.HandoverListQuery.class);
        verify(getHandoverListUseCase).list(captor.capture());
        assertThat(captor.getValue().teamId()).isEqualTo(TEAM);
        assertThat(captor.getValue().writerMemberId()).isNull();
    }

    @Test
    void getPackageMapsReferenceDateAndReturnsPackage() throws Exception {
        when(getHandoverPackageUseCase.getPackage(eq(HANDOVER_ID), eq(LocalDate.of(2026, 8, 4))))
                .thenReturn(packageResult());

        mockMvc.perform(get("/api/handovers/{handoverId}", HANDOVER_ID)
                        .param("referenceDate", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.basicInfo.writerName").value("Kim"))
                .andExpect(jsonPath("$.data.items[0].actionId").value(ACTION));

        verify(getHandoverPackageUseCase).getPackage(HANDOVER_ID, LocalDate.of(2026, 8, 4));
    }

    @Test
    void handoverToSuccessorMapsBodyAndPathToUseCase() throws Exception {
        when(handoverToSuccessorUseCase.handoverToSuccessor(
                eq(HANDOVER_ID), eq(TARGET), eq(APPROVER), eq("Owner"), any(LocalDateTime.class)))
                .thenReturn(finalized());

        mockMvc.perform(post("/api/handovers/{handoverId}/handover-to-successor", HANDOVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "successorId": 2,
                                  "ownerId": 9,
                                  "ownerName": "Owner"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINALIZED"));

        verify(handoverToSuccessorUseCase).handoverToSuccessor(
                eq(HANDOVER_ID), eq(TARGET), eq(APPROVER), eq("Owner"), any(LocalDateTime.class));
    }

    private static GetHandoverListUseCase.HandoverSummary summary() {
        return new GetHandoverListUseCase.HandoverSummary(HANDOVER_ID, WRITER, "Kim", "Manager", TEAM,
                HandoverType.VACATION, HandoverStatus.SUBMITTED, START, END, null, 1, 1, 0);
    }

    private static GetHandoverPackageUseCase.HandoverPackage packageResult() {
        GetHandoverPackageUseCase.Item item = new GetHandoverPackageUseCase.Item(
                ACTION, "Action", "TODO", LocalDate.of(2026, 8, 30), "PRJ", "Meeting");
        return new GetHandoverPackageUseCase.HandoverPackage(
                new GetHandoverPackageUseCase.BasicInfo("Kim", "Manager", TEAM, HandoverType.VACATION, START, END, null),
                new GetHandoverPackageUseCase.GapSummary(1, 1, 0),
                List.of(item),
                List.of(new GetHandoverPackageUseCase.ContextCard(ACTION, "Action", "Content")),
                List.of(new GetHandoverPackageUseCase.MeetingHistory(
                        500L, LocalDate.of(2026, 8, 1), List.of("Kim"), "Decision", "Actions")),
                List.of(new GetHandoverPackageUseCase.ReassigneeGroup(null, "Unassigned", List.of(item)))
        );
    }

    private static Handover submitted() {
        return Handover.restore(HANDOVER_ID, WRITER, TEAM, HandoverType.VACATION, HandoverStatus.SUBMITTED,
                START, END, null, "Kim", "Manager", null, null, null, null, null,
                null, null, 1L, List.of(item()));
    }

    private static Handover reassigned() {
        return Handover.restore(HANDOVER_ID, WRITER, TEAM, HandoverType.VACATION, HandoverStatus.REASSIGNED,
                START, END, null, "Kim", "Manager", APPROVER, "Park", LocalDateTime.now(), null,
                null, null, null, 1L, List.of(item()));
    }

    private static Handover finalized() {
        return Handover.restore(HANDOVER_ID, WRITER, TEAM, HandoverType.VACATION, HandoverStatus.FINALIZED,
                START, END, null, "Kim", "Manager", APPROVER, "Park", LocalDateTime.now(), null,
                LocalDateTime.now(), APPROVER, "Park", 1L, List.of(item()));
    }

    private static Handover rejected() {
        return Handover.restore(HANDOVER_ID, WRITER, TEAM, HandoverType.VACATION, HandoverStatus.REJECTED,
                START, END, null, "Kim", "Manager", null, null, null, "needs more detail",
                null, null, null, 1L, List.of(item()));
    }

    private static HandoverItem item() {
        return HandoverItem.create(ACTION, "Action", "TODO", "PRJ", "TEAM",
                LocalDate.of(2026, 8, 30), 500L, "Meeting", "Content", true);
    }
}
