package com.module06.backend.calendar.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.calendar.domain.model.PersonalTodo;
import com.module06.backend.calendar.domain.repository.PersonalTodoRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PersonalTodoPersistenceAdapterTest {

    private static final Long COMPANY = 1L;
    private static final Long MEMBER = 5L;

    @Autowired
    private PersonalTodoRepository personalTodoRepository;

    @Test
    void savesAndFindsByIdWithGeneratedIdAndTimestamps() {
        PersonalTodo saved = personalTodoRepository.save(
                PersonalTodo.create(COMPANY, MEMBER, "우유 사기", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20)));

        assertThat(saved.getId()).isNotNull();

        Optional<PersonalTodo> found = personalTodoRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("우유 사기");
        assertThat(found.get().getEndDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(found.get().isDone()).isFalse();
    }

    @Test
    void findsAllByMemberIdOverlappingPeriodOnly() {
        personalTodoRepository.save(PersonalTodo.create(
                COMPANY, MEMBER, "범위 안", LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15)));
        personalTodoRepository.save(PersonalTodo.create(
                COMPANY, MEMBER, "범위 밖", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)));
        personalTodoRepository.save(PersonalTodo.create(
                COMPANY, 999L, "다른 사람", LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15)));

        List<PersonalTodo> result = personalTodoRepository.findAllByMemberIdOverlappingPeriod(
                MEMBER, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("범위 안");
    }

    @Test
    void findsTodoThatStartsBeforeMonthButEndsInsideIt() {
        personalTodoRepository.save(PersonalTodo.create(
                COMPANY, MEMBER, "걸치는 일정", LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 3)));

        List<PersonalTodo> result = personalTodoRepository.findAllByMemberIdOverlappingPeriod(
                MEMBER, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("걸치는 일정");
    }

    @Test
    void persistsToggledDoneState() {
        PersonalTodo saved = personalTodoRepository.save(
                PersonalTodo.create(COMPANY, MEMBER, "우유 사기", LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 20)));
        saved.toggleDone();

        personalTodoRepository.save(saved);

        assertThat(personalTodoRepository.findById(saved.getId()).orElseThrow().isDone()).isTrue();
    }
}
