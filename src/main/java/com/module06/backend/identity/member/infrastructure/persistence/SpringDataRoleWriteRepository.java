package com.module06.backend.identity.member.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRoleWriteRepository extends JpaRepository<RoleWriteEntity, Long> {
}
