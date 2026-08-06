package com.module06.backend.identity.position.infrastructure.persistence;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.position.domain.model.Position;
import com.module06.backend.identity.position.domain.repository.PositionRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PositionPersistenceAdapter implements PositionRepository {

    /* 같은 회사 안 직급 이름 유일성을 최종 차단하는 데이터베이스 제약 이름이다(V2.3.15). */
    private static final String POSITION_NAME_UNIQUE_CONSTRAINT = "UK_POSITION_COMPANY_NAME";

    private final SpringDataPositionRepository repository;

    @Override
    public List<Position> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Position> findByIdAndCompanyId(Long id, Long companyId) {
        return repository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public Position create(Long companyId, String name, Authority authority, String description) {
        PositionJpaEntity saved;
        try {
            /* flush까지 실행해 유일성 제약 위반을 이 메서드 경계에서 확인한다. */
            saved = repository.saveAndFlush(PositionJpaEntity.create(companyId, name, authority, description));
        } catch (DataIntegrityViolationException exception) {
            throw translateNameDuplicate(exception);
        }
        return toDomain(saved);
    }

    @Override
    @Transactional
    public void update(Long id, String name, Authority authority, String description) {
        repository.findById(id).ifPresent(entity -> entity.update(name, authority, description));
        try {
            /* 커밋까지 기다리지 않고 이 메서드 경계에서 유일성 제약 위반을 확인한다. */
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw translateNameDuplicate(exception);
        }
    }

    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }

    @Override
    public boolean existsByCompanyIdAndName(Long companyId, String name) {
        return repository.existsByCompanyIdAndName(companyId, name);
    }

    private RuntimeException translateNameDuplicate(DataIntegrityViolationException exception) {
        if (containsConstraintName(exception, POSITION_NAME_UNIQUE_CONSTRAINT)) {
            return new BusinessException(AuthErrorCode.POSITION_NAME_DUPLICATED, exception);
        }
        return exception;
    }

    private boolean containsConstraintName(Throwable exception, String constraintName) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toUpperCase(Locale.ROOT).contains(constraintName.toUpperCase(Locale.ROOT))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Position toDomain(PositionJpaEntity entity) {
        return new Position(entity.getId(), entity.getCompanyId(), entity.getName(), entity.getAuthority(),
                entity.getDescription());
    }
}
