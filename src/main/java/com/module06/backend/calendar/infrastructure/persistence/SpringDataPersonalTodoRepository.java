package com.module06.backend.calendar.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPersonalTodoRepository extends JpaRepository<PersonalTodoJpaEntity, Long> {

    List<PersonalTodoJpaEntity> findAllByMemberIdAndDateBetween(Long memberId, LocalDate from, LocalDate to);
}
