package com.module06.backend.identity.team.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.module06.backend.global.exception.BusinessException;

/*
 * team 이름 유일성 제약(V2.3.13, UK_TEAM_COMPANY_PARENT_NAME) 위반을
 * TEAM_NAME_DUPLICATED(AU-016)로 변환하는지 검증한다.
 */
@DisplayName("Team 저장/수정 예외 변환")
class TeamPersistenceAdapterUnitTest {

    @Test
    @DisplayName("생성 시 이름 유일성 제약 위반을 AU-016으로 변환한다")
    void translatesNameConstraintViolationOnCreate() {
        SpringDataTeamRepository repository = mock(SpringDataTeamRepository.class);
        when(repository.saveAndFlush(any(TeamJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for key 'UK_TEAM_COMPANY_PARENT_NAME'"
                ));
        TeamPersistenceAdapter adapter = new TeamPersistenceAdapter(repository);

        assertThatThrownBy(() -> adapter.create(1L, null, "본부"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("AU-016");
    }

    @Test
    @DisplayName("수정 시 이름 유일성 제약 위반을 AU-016으로 변환한다")
    void translatesNameConstraintViolationOnRename() {
        SpringDataTeamRepository repository = mock(SpringDataTeamRepository.class);
        when(repository.findById(1L)).thenReturn(java.util.Optional.empty());
        doThrow(new DataIntegrityViolationException(
                "Duplicate entry for key 'UK_TEAM_COMPANY_PARENT_NAME'"
        )).when(repository).flush();
        TeamPersistenceAdapter adapter = new TeamPersistenceAdapter(repository);

        assertThatThrownBy(() -> adapter.rename(1L, "본부"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("AU-016");
    }

    @Test
    @DisplayName("이름 중복과 무관한 무결성 오류는 원래 예외로 유지한다")
    void preservesUnrelatedIntegrityViolation() {
        DataIntegrityViolationException original = new DataIntegrityViolationException(
                "Foreign key constraint violation"
        );
        SpringDataTeamRepository repository = mock(SpringDataTeamRepository.class);
        when(repository.saveAndFlush(any(TeamJpaEntity.class))).thenThrow(original);
        TeamPersistenceAdapter adapter = new TeamPersistenceAdapter(repository);

        assertThatThrownBy(() -> adapter.create(1L, null, "본부")).isSameAs(original);
    }
}
