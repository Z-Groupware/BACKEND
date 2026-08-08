package com.module06.backend.calendar.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.module06.backend.calendar.domain.model.PersonalTodo;
import com.module06.backend.calendar.domain.repository.PersonalTodoRepository;

import lombok.RequiredArgsConstructor;

/* comment.
    domain의 PersonalTodoRepository 계약을 JPA로 구현하는 어댑터. ActionPersistenceAdapter와
    같은 패턴 — Spring Data 호출 위임 + 엔티티 ↔ 도메인 변환.
*/
@Component
@RequiredArgsConstructor
public class PersonalTodoPersistenceAdapter implements PersonalTodoRepository {

    private final SpringDataPersonalTodoRepository springDataPersonalTodoRepository;

    @Override
    public PersonalTodo save(PersonalTodo todo) {
        return toDomain(springDataPersonalTodoRepository.save(toEntity(todo)));
    }

    @Override
    public Optional<PersonalTodo> findById(Long id) {
        return springDataPersonalTodoRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<PersonalTodo> findAllByMemberIdAndDateBetween(Long memberId, LocalDate from, LocalDate to) {
        return springDataPersonalTodoRepository.findAllByMemberIdAndDateBetween(memberId, from, to).stream()
                .map(this::toDomain)
                .toList();
    }

    private PersonalTodoJpaEntity toEntity(PersonalTodo todo) {
        return PersonalTodoJpaEntity.builder()
                .id(todo.getId())
                .companyId(todo.getCompanyId())
                .memberId(todo.getMemberId())
                .title(todo.getTitle())
                .date(todo.getDate())
                .isDone(todo.isDone())
                .build();
    }

    private PersonalTodo toDomain(PersonalTodoJpaEntity entity) {
        return PersonalTodo.reconstitute(
                entity.getId(),
                entity.getCompanyId(),
                entity.getMemberId(),
                entity.getTitle(),
                entity.getDate(),
                entity.isDone(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
