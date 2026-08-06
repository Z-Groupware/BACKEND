package com.module06.backend.identity.position.domain.model;

import com.module06.backend.identity.member.domain.model.Authority;

/** 화면의 "직급"(사원·대리·선임·팀장 등) — team 과 달리 계층이 없는 flat 목록이다. */
public record Position(Long id, Long companyId, String name, Authority authority, String description) {
}
