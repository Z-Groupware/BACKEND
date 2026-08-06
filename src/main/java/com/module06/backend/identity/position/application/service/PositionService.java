package com.module06.backend.identity.position.application.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.position.application.command.CreatePositionCommand;
import com.module06.backend.identity.position.application.command.UpdatePositionCommand;
import com.module06.backend.identity.position.application.dto.PositionSummary;
import com.module06.backend.identity.position.application.port.out.PositionMemberQueryPort;
import com.module06.backend.identity.position.application.port.out.PositionMemberQueryPort.PositionMemberSummary;
import com.module06.backend.identity.position.application.usecase.CreatePositionUseCase;
import com.module06.backend.identity.position.application.usecase.DeletePositionUseCase;
import com.module06.backend.identity.position.application.usecase.GetPositionsUseCase;
import com.module06.backend.identity.position.application.usecase.UpdatePositionUseCase;
import com.module06.backend.identity.position.domain.model.Position;
import com.module06.backend.identity.position.domain.repository.PositionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PositionService implements GetPositionsUseCase, CreatePositionUseCase, UpdatePositionUseCase,
        DeletePositionUseCase {

    private final PositionRepository positionRepository;
    private final PositionMemberQueryPort memberQueryPort;

    @Override
    @Transactional(readOnly = true)
    public List<PositionSummary> getPositions(Long companyId) {
        List<Position> positions = positionRepository.findByCompanyId(companyId);
        Map<Long, Long> memberCountByPosition = countActiveMembersByPosition(companyId);
        return positions.stream()
                .sorted((a, b) -> Long.compare(a.id(), b.id()))
                .map(position -> toSummary(position, memberCountByPosition))
                .toList();
    }

    @Override
    @Transactional
    public PositionSummary create(CreatePositionCommand command) {
        assertAssignable(command.authority());

        if (positionRepository.existsByCompanyIdAndName(command.companyId(), command.name())) {
            throw new BusinessException(AuthErrorCode.POSITION_NAME_DUPLICATED);
        }

        Position created = positionRepository.create(
                command.companyId(), command.name(), command.authority(), command.description());
        return new PositionSummary(created.id(), created.name(), created.authority(), created.description(), 0L);
    }

    /**
     * 기존 구성원의 authority 에는 소급 적용하지 않는다(§6-8) — 직급 행만 바꾸고 member 테이블은
     * 건드리지 않는다. 이 직급으로 이미 발급된 계정의 권한은 그대로 유지된다.
     */
    @Override
    @Transactional
    public PositionSummary update(UpdatePositionCommand command) {
        assertAssignable(command.authority());

        Position position = positionRepository.findByIdAndCompanyId(command.positionId(), command.companyId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.POSITION_NOT_FOUND));

        boolean renamingToOtherName = !command.name().equals(position.name());
        if (renamingToOtherName && positionRepository.existsByCompanyIdAndName(command.companyId(), command.name())) {
            throw new BusinessException(AuthErrorCode.POSITION_NAME_DUPLICATED);
        }

        positionRepository.update(position.id(), command.name(), command.authority(), command.description());

        Map<Long, Long> memberCountByPosition = countActiveMembersByPosition(command.companyId());
        Position updated = new Position(position.id(), position.companyId(), command.name(), command.authority(),
                command.description());
        return toSummary(updated, memberCountByPosition);
    }

    @Override
    @Transactional
    public void delete(Long companyId, Long positionId) {
        Position position = positionRepository.findByIdAndCompanyId(positionId, companyId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.POSITION_NOT_FOUND));

        if (memberQueryPort.hasActiveMembers(position.id())) {
            throw new BusinessException(AuthErrorCode.POSITION_IN_USE);
        }
        positionRepository.delete(position.id());
    }

    /** 직급으로 권한을 상승시킬 경로를 없앤다(§0-1, §6-7) — OWNER 는 직급 매핑으로 만들 수 없다. */
    private void assertAssignable(Authority authority) {
        if (authority != Authority.LEADER && authority != Authority.MEMBER) {
            throw new BusinessException(AuthErrorCode.POSITION_ROLE_NOT_ASSIGNABLE);
        }
    }

    private Map<Long, Long> countActiveMembersByPosition(Long companyId) {
        List<PositionMemberSummary> members = memberQueryPort.findActiveMembersByCompany(companyId);
        return members.stream()
                .filter(m -> m.positionId() != null)
                .collect(Collectors.groupingBy(PositionMemberSummary::positionId, Collectors.counting()));
    }

    private PositionSummary toSummary(Position position, Map<Long, Long> memberCountByPosition) {
        long memberCount = memberCountByPosition.getOrDefault(position.id(), 0L);
        return new PositionSummary(position.id(), position.name(), position.authority(), position.description(),
                memberCount);
    }
}
