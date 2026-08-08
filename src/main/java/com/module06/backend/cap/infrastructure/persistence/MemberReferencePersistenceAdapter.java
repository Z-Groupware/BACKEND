package com.module06.backend.cap.infrastructure.persistence;

import com.module06.backend.cap.domain.repository.MemberReferenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// domain의 MemberReferenceRepository 계약을 JPA로 구현하는 어댑터. action.ActionReferenceRepositoryAdapter의
// findMemberReferences와 동일한 배치 조회 패턴(findAllById).
@Repository
public class MemberReferencePersistenceAdapter implements MemberReferenceRepository {

    private final SpringDataCapMemberReferenceRepository springDataCapMemberReferenceRepository;

    public MemberReferencePersistenceAdapter(SpringDataCapMemberReferenceRepository springDataCapMemberReferenceRepository) {
        this.springDataCapMemberReferenceRepository = springDataCapMemberReferenceRepository;
    }

    @Override
    public List<MemberName> findNames(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return springDataCapMemberReferenceRepository.findAllById(memberIds).stream()
                .map(member -> new MemberName(member.getId(), member.getName()))
                .toList();
    }
}
