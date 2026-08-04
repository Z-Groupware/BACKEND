package com.module06.backend.handover.presentation.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.GlobalExceptionHandler;
import com.module06.backend.handover.application.usecase.CompleteHandoverUseCase;
import com.module06.backend.handover.application.usecase.CreateHandoverUseCase;
import com.module06.backend.handover.application.usecase.FinalizeHandoverUseCase;
import com.module06.backend.handover.application.usecase.GetHandoverListUseCase;
import com.module06.backend.handover.application.usecase.GetHandoverPackageUseCase;
import com.module06.backend.handover.application.usecase.HandoverToSuccessorUseCase;
import com.module06.backend.handover.application.usecase.ReassignHandoverItemUseCase;
import com.module06.backend.handover.application.usecase.RejectHandoverUseCase;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;
import com.module06.backend.handover.presentation.api.request.CreateHandoverRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HandoverControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private CreateHandoverUseCase createHandoverUseCase;
    private ReassignHandoverItemUseCase reassignHandoverItemUseCase;
    private CompleteHandoverUseCase completeHandoverUseCase;
    private FinalizeHandoverUseCase finalizeHandoverUseCase;
    private RejectHandoverUseCase rejectHandoverUseCase;
    private GetHandoverPackageUseCase getHandoverPackageUseCase;
    private GetHandoverListUseCase getHandoverListUseCase;
    private HandoverToSuccessorUseCase handoverToSuccessorUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createHandoverUseCase = mock(CreateHandoverUseCase.class);
        reassignHandoverItemUseCase = mock(ReassignHandoverItemUseCase.class);
        completeHandoverUseCase = mock(CompleteHandoverUseCase.class);
        finalizeHandoverUseCase = mock(FinalizeHandoverUseCase.class);
        rejectHandoverUseCase = mock(RejectHandoverUseCase.class);
        getHandoverPackageUseCase = mock(GetHandoverPackageUseCase.class);
        getHandoverListUseCase = mock(GetHandoverListUseCase.class);
        handoverToSuccessorUseCase = mock(HandoverToSuccessorUseCase.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new HandoverController(
                        createHandoverUseCase,
                        reassignHandoverItemUseCase,
                        completeHandoverUseCase,
                        finalizeHandoverUseCase,
                        rejectHandoverUseCase,
                        getHandoverPackageUseCase,
                        getHandoverListUseCase,
                        handoverToSuccessorUseCase
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createReturnsCreatedApiResponse() throws Exception {
        when(createHandoverUseCase.create(any())).thenReturn(Handover.restore(
                1000L, 1L, 10L, HandoverType.VACATION, HandoverStatus.SUBMITTED,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 20),
                null, "Kim", "Manager", null, null, null, null,
                null, null, null, 0L, List.of()
        ));
        CreateHandoverRequest request = new CreateHandoverRequest(
                1L, 10L, HandoverType.VACATION,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 20),
                null, List.of(100L)
        );

        mockMvc.perform(post("/api/handovers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.httpStatus").value(201))
                .andExpect(jsonPath("$.data.id").value(1000))
                .andExpect(jsonPath("$.data.handoverType").value("VACATION"));
    }

    @Test
    void reassignReturnsApiResponse() throws Exception {
        when(reassignHandoverItemUseCase.reassignItem(any())).thenReturn(submittedHandover());

        mockMvc.perform(patch("/api/handovers/{handoverId}/items/{actionId}/reassign", 1000L, 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toMemberId": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.data.id").value(1000));
    }

    @Test
    void completeReturnsApiResponse() throws Exception {
        when(completeHandoverUseCase.complete(eq(1000L), eq(9L), any(LocalDateTime.class)))
                .thenReturn(reassignedHandover());

        mockMvc.perform(post("/api/handovers/{handoverId}/complete", 1000L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "leaderId": 9
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REASSIGNED"));
    }

    @Test
    void finalizeReturnsApiResponse() throws Exception {
        when(finalizeHandoverUseCase.finalize(eq(1000L), eq(99L), eq("Owner"), any(LocalDateTime.class)))
                .thenReturn(finalizedHandover());

        mockMvc.perform(post("/api/handovers/{handoverId}/finalize", 1000L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approverId": 99,
                                  "approverName": "Owner"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FINALIZED"))
                .andExpect(jsonPath("$.data.finalApproverNameSnap").value("Owner"));
    }

    @Test
    void rejectReturnsApiResponse() throws Exception {
        when(rejectHandoverUseCase.reject(any())).thenReturn(rejectedHandover());

        mockMvc.perform(post("/api/handovers/{handoverId}/reject", 1000L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "needs detail"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("needs detail"));
    }

    @Test
    void getPackageReturnsApiResponse() throws Exception {
        when(getHandoverPackageUseCase.getPackage(eq(1000L), eq(LocalDate.of(2026, 8, 4))))
                .thenReturn(new GetHandoverPackageUseCase.HandoverPackage(
                        new GetHandoverPackageUseCase.BasicInfo(
                                "Kim", "Manager", 10L, HandoverType.VACATION,
                                LocalDate.of(2026, 8, 10),
                                LocalDate.of(2026, 8, 20),
                                null
                        ),
                        new GetHandoverPackageUseCase.GapSummary(1, 1, 1),
                        List.of(new GetHandoverPackageUseCase.Item(
                                100L, "Prepare", "TODO", LocalDate.of(2026, 8, 8), "PRJ", "Weekly"
                        )),
                        List.of(new GetHandoverPackageUseCase.ContextCard(100L, "Prepare", "Context")),
                        List.of(new GetHandoverPackageUseCase.MeetingHistory(
                                700L, LocalDate.of(2026, 8, 1), List.of("Kim"), "Decision", "Actions"
                        )),
                        List.of(new GetHandoverPackageUseCase.ReassigneeGroup(
                                null, "미배정", List.of(new GetHandoverPackageUseCase.Item(
                                100L, "Prepare", "TODO", LocalDate.of(2026, 8, 8), "PRJ", "Weekly"
                        ))
                        ))
                ));

        mockMvc.perform(get("/api/handovers/{handoverId}", 1000L)
                        .param("referenceDate", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.basicInfo.writerName").value("Kim"))
                .andExpect(jsonPath("$.data.items[0].actionId").value(100));
    }

    @Test
    void createValidationFailureReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/handovers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamId": 10,
                                  "handoverType": "VACATION"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void businessExceptionMapsToConfiguredStatus() throws Exception {
        when(getHandoverPackageUseCase.getPackage(eq(404L), any(LocalDate.class)))
                .thenThrow(new BusinessException(HandoverErrorCode.HO_NOT_FOUND));

        mockMvc.perform(get("/api/handovers/{handoverId}", 404L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("HO-014"));
    }

    private static Handover submittedHandover() {
        return Handover.restore(
                1000L, 1L, 10L, HandoverType.VACATION, HandoverStatus.SUBMITTED,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 20),
                null, "Kim", "Manager", null, null, null, null,
                null, null, null, 0L, List.of()
        );
    }

    private static Handover reassignedHandover() {
        return Handover.restore(
                1000L, 1L, 10L, HandoverType.VACATION, HandoverStatus.REASSIGNED,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 20),
                null, "Kim", "Manager", 9L, "Leader", LocalDateTime.of(2026, 8, 4, 9, 0), null,
                null, null, null, 1L, List.of()
        );
    }

    private static Handover finalizedHandover() {
        return Handover.restore(
                1000L, 1L, 10L, HandoverType.VACATION, HandoverStatus.FINALIZED,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 20),
                null, "Kim", "Manager", 9L, "Leader", LocalDateTime.of(2026, 8, 4, 9, 0), null,
                LocalDateTime.of(2026, 8, 5, 9, 0), 99L, "Owner", 2L, List.of()
        );
    }

    private static Handover rejectedHandover() {
        return Handover.restore(
                1000L, 1L, 10L, HandoverType.VACATION, HandoverStatus.REJECTED,
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 20),
                null, "Kim", "Manager", null, null, null, "needs detail",
                null, null, null, 1L, List.of()
        );
    }
}
