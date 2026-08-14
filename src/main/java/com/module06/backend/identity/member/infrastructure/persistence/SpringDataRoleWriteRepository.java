package com.module06.backend.identity.member.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRoleWriteRepository extends JpaRepository<RoleWriteEntity, Long> {

    /**
     * §7-4 역할 라벨을 이름으로 되돌린다. 회사 안에서만 찾는다 — 이름은 회사마다 겹칠 수 있어
     * 회사 조건이 빠지면 남의 회사 역할이 붙는다.
     *
     * <p>{@code First} 를 붙이는 이유: {@code role} 에는 (company_id, name) UNIQUE 가 없다.
     * 같은 이름이 두 부서에 하나씩 있을 수 있고, 그때 {@code findBy...} 하나짜리 시그니처는
     * {@code IncorrectResultSizeDataAccessException} 으로 500 을 낸다. 역할은 인가에 쓰지 않는
     * 표시용 라벨이라 같은 이름 중 어느 행을 잡아도 화면 결과가 같다.
     */
    Optional<RoleWriteEntity> findFirstByCompanyIdAndName(Long companyId, String name);
}
