package com.module06.backend.identity.member.application.dto;

import java.time.LocalDate;

import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;

/**
 * 구성원 목록 행(§7-1). {@code roleLabel} 은 화면 "역할"(프론트엔드·백엔드)이고 검색 대상이 아니다 —
 * {@code role} 은 화면 "권한"(LEADER·MEMBER·OWNER)이다. avatarColor 는 담지 않는다 — 이름으로 색을
 * 뽑는 것은 순수 계산이라 프론트 몫이다({@link com.module06.backend.identity.member.application.dto.MyProfile}
 * 과 같은 결정).
 */
public record MemberListItem(
        Long memberId,
        String name,
        String teamName,
        String positionName,
        Authority role,
        boolean isAdmin,
        String roleLabel,
        MemberStatus workStatus,
        LocalDate joinedOn
) {
}
