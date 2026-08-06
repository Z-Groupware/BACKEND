package com.module06.backend.identity.position.presentation.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.identity.position.application.command.CreatePositionCommand;
import com.module06.backend.identity.position.application.command.UpdatePositionCommand;
import com.module06.backend.identity.position.application.dto.PositionSummary;
import com.module06.backend.identity.position.application.usecase.CreatePositionUseCase;
import com.module06.backend.identity.position.application.usecase.DeletePositionUseCase;
import com.module06.backend.identity.position.application.usecase.GetPositionsUseCase;
import com.module06.backend.identity.position.application.usecase.UpdatePositionUseCase;
import com.module06.backend.identity.position.presentation.api.dto.request.CreatePositionRequest;
import com.module06.backend.identity.position.presentation.api.dto.request.UpdatePositionRequest;
import com.module06.backend.identity.position.presentation.api.dto.response.PositionResponse;

import lombok.RequiredArgsConstructor;

@Tag(name = "Identity", description = "직급(Position) CRUD API")
@RestController
@RequestMapping("/api/job-positions")
@RequiredArgsConstructor
public class PositionController {

    private final GetPositionsUseCase getPositionsUseCase;
    private final CreatePositionUseCase createPositionUseCase;
    private final UpdatePositionUseCase updatePositionUseCase;
    private final DeletePositionUseCase deletePositionUseCase;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<PositionResponse>> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId) {
        List<PositionResponse> response = getPositionsUseCase.getPositions(companyId).stream()
                .map(PositionResponse::from)
                .toList();
        return ApiResponse.success("직급 목록을 조회했습니다", response);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<PositionResponse> create(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @Valid @RequestBody CreatePositionRequest request) {
        PositionSummary summary = createPositionUseCase.create(
                new CreatePositionCommand(companyId, request.name(), request.authority(), request.description()));
        return ApiResponse.created("직급을 생성했습니다", PositionResponse.from(summary));
    }

    @PatchMapping("/{positionId}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<PositionResponse> update(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long positionId,
            @Valid @RequestBody UpdatePositionRequest request) {
        PositionSummary summary = updatePositionUseCase.update(new UpdatePositionCommand(
                companyId, positionId, request.name(), request.authority(), request.description()));
        return ApiResponse.success("직급을 수정했습니다", PositionResponse.from(summary));
    }

    @DeleteMapping("/{positionId}")
    @PreAuthorize("hasRole('OWNER')")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "companyId") Long companyId,
            @PathVariable Long positionId) {
        deletePositionUseCase.delete(companyId, positionId);
        return ApiResponse.successWithoutData("직급을 삭제했습니다");
    }
}
