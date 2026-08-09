package com.module06.backend.identity.member.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.application.command.UpdateMyProfileCommand;
import com.module06.backend.identity.member.application.dto.MyProfile;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryCommandPort;
import com.module06.backend.identity.member.application.port.out.MyProfileCommandPort;
import com.module06.backend.identity.member.application.port.out.MyProfileQueryPort;
import com.module06.backend.identity.member.application.usecase.GetMyProfileUseCase;
import com.module06.backend.identity.member.application.usecase.UpdateMyProfileUseCase;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.position.domain.repository.PositionRepository;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyProfileService implements GetMyProfileUseCase, UpdateMyProfileUseCase {

    private final MyProfileQueryPort myProfileQueryPort;
    private final MyProfileCommandPort myProfileCommandPort;
    private final TeamRepository teamRepository;
    private final PositionRepository positionRepository;
    private final MemberDirectoryCommandPort memberCommandPort;
    private final RefreshTokenStore refreshTokenStore;

    @Override
    @Transactional(readOnly = true)
    public MyProfile get(Long memberId) {
        return findProfile(memberId);
    }

    /**
     * 부서·직급·전화번호만 셀프로 바꾼다 — 권한(authority)·isAdmin·이름·이메일은 이 경로로 절대
     * 바뀌지 않는다. 직급의 defaultRole(LEADER/MEMBER)이 있어도 자동으로 내 authority를 올리지
     * 않는다(§7-4와 같은 원칙 — 권한은 항상 명시적으로만 바뀐다).
     */
    @Override
    @Transactional
    public MyProfile update(UpdateMyProfileCommand command) {
        MyProfile current = findProfile(command.memberId());

        if (command.teamId() != null) {
            teamRepository.findByIdAndCompanyId(command.teamId(), command.companyId())
                    .orElseThrow(() -> new BusinessException(AuthErrorCode.TEAM_NOT_FOUND));
        }
        if (command.positionId() != null) {
            positionRepository.findByIdAndCompanyId(command.positionId(), command.companyId())
                    .orElseThrow(() -> new BusinessException(AuthErrorCode.POSITION_NOT_FOUND));
        }

        /*
         * 팀장이 조용히 다른 팀으로 옮겨가면 옛 팀엔 이제 없는 사람이 팀장으로 남는다. 팀을 실제로
         * 바꾸는 경우에만 강등한다 — 직급·전화번호만 바꾸거나 같은 teamId를 다시 보내면 건드리지
         * 않는다. 권한이 바뀌므로 강등 시 리프레시도 전부 폐기한다(§7-4 팀장 교체와 같은 컨벤션).
         */
        boolean changesTeam = command.teamId() != null && !command.teamId().equals(current.teamId());
        if (changesTeam && current.authority() == Authority.LEADER) {
            teamRepository.updateLeader(current.teamId(), null);
            memberCommandPort.demoteToMember(command.memberId());
            refreshTokenStore.revokeAllByMember(command.memberId());
        }

        myProfileCommandPort.updateProfile(command.memberId(), command.teamId(), command.positionId(), command.phone());

        return findProfile(command.memberId());
    }

    private MyProfile findProfile(Long memberId) {
        return myProfileQueryPort.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));
    }
}
