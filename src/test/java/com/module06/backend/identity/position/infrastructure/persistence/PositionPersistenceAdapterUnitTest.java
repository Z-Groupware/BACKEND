package com.module06.backend.identity.position.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.member.domain.model.Authority;

/*
 * position 이름 유일성 제약(V2.3.15, UK_POSITION_COMPANY_NAME) 위반을
 * POSITION_NAME_DUPLICATED(AU-022)로 변환하는지 검증한다.
 */
@DisplayName("Position 저장/수정 예외 변환")
class PositionPersistenceAdapterUnitTest {

    @Test
    @DisplayName("생성 시 이름 유일성 제약 위반을 AU-022로 변환한다")
    void translatesNameConstraintViolationOnCreate() {
        SpringDataPositionRepository repository = mock(SpringDataPositionRepository.class);
        when(repository.saveAndFlush(any(PositionJpaEntity.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry for key 'UK_POSITION_COMPANY_NAME'"
                ));
        PositionPersistenceAdapter adapter = new PositionPersistenceAdapter(repository);

        assertThatThrownBy(() -> adapter.create(1L, "사원", Authority.MEMBER, "설명"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("AU-022");
    }

    @Test
    @DisplayName("수정 시 이름 유일성 제약 위반을 AU-022로 변환한다")
    void translatesNameConstraintViolationOnUpdate() {
        SpringDataPositionRepository repository = mock(SpringDataPositionRepository.class);
        when(repository.findById(1L))
                .thenReturn(java.util.Optional.of(PositionJpaEntity.create(1L, "사원", Authority.MEMBER, "설명")));
        doThrow(new DataIntegrityViolationException(
                "Duplicate entry for key 'UK_POSITION_COMPANY_NAME'"
        )).when(repository).flush();
        PositionPersistenceAdapter adapter = new PositionPersistenceAdapter(repository);

        assertThatThrownBy(() -> adapter.update(1L, "사원", Authority.MEMBER, "설명"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("AU-022");
    }

    @Test
    @DisplayName("수정 대상이 없으면(동시 삭제 등) AU-020으로 실패한다")
    void throwsNotFoundWhenUpdateTargetIsGone() {
        SpringDataPositionRepository repository = mock(SpringDataPositionRepository.class);
        when(repository.findById(1L)).thenReturn(java.util.Optional.empty());
        PositionPersistenceAdapter adapter = new PositionPersistenceAdapter(repository);

        assertThatThrownBy(() -> adapter.update(1L, "사원", Authority.MEMBER, "설명"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().getCode())
                .isEqualTo("AU-020");
    }

    @Test
    @DisplayName("이름 중복과 무관한 무결성 오류는 원래 예외로 유지한다")
    void preservesUnrelatedIntegrityViolation() {
        DataIntegrityViolationException original = new DataIntegrityViolationException(
                "Foreign key constraint violation"
        );
        SpringDataPositionRepository repository = mock(SpringDataPositionRepository.class);
        when(repository.saveAndFlush(any(PositionJpaEntity.class))).thenThrow(original);
        PositionPersistenceAdapter adapter = new PositionPersistenceAdapter(repository);

        assertThatThrownBy(() -> adapter.create(1L, "사원", Authority.MEMBER, "설명")).isSameAs(original);
    }
}
