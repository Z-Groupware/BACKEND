package com.module06.backend.identity.position.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.position.application.command.CreatePositionCommand;
import com.module06.backend.identity.position.application.command.UpdatePositionCommand;
import com.module06.backend.identity.position.application.dto.PositionSummary;
import com.module06.backend.identity.position.application.port.out.PositionMemberQueryPort;
import com.module06.backend.identity.position.domain.model.Position;
import com.module06.backend.identity.position.domain.repository.PositionRepository;

@DisplayName("직급 CRUD")
class PositionServiceTest {

    @Test
    @DisplayName("직급을 생성한다")
    void createsPosition() {
        FakePositionRepository repository = new FakePositionRepository();

        PositionSummary summary = service(repository, new FakeMemberQueryPort())
                .create(new CreatePositionCommand(1L, "수석", Authority.LEADER, "팀 회의 개설"));

        assertThat(summary.name()).isEqualTo("수석");
        assertThat(summary.authority()).isEqualTo(Authority.LEADER);
        assertThat(summary.description()).isEqualTo("팀 회의 개설");
        assertThat(summary.memberCount()).isZero();
    }

    @Test
    @DisplayName("OWNER 권한으로는 생성할 수 없다")
    void rejectsCreatingWithOwnerAuthority() {
        FakePositionRepository repository = new FakePositionRepository();

        assertThatThrownBy(() -> service(repository, new FakeMemberQueryPort())
                .create(new CreatePositionCommand(1L, "오너직급", Authority.OWNER, "설명")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.POSITION_ROLE_NOT_ASSIGNABLE);
    }

    @Test
    @DisplayName("같은 회사 안 이름이 중복되면 거절한다")
    void rejectsDuplicateNameInSameCompany() {
        FakePositionRepository repository = new FakePositionRepository();
        repository.create(1L, "사원", Authority.MEMBER, "설명");

        assertThatThrownBy(() -> service(repository, new FakeMemberQueryPort())
                .create(new CreatePositionCommand(1L, "사원", Authority.MEMBER, "다른 설명")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.POSITION_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("다른 회사라면 같은 이름이어도 허용한다")
    void allowsSameNameAcrossCompanies() {
        FakePositionRepository repository = new FakePositionRepository();
        repository.create(1L, "사원", Authority.MEMBER, "설명");

        PositionSummary summary = service(repository, new FakeMemberQueryPort())
                .create(new CreatePositionCommand(2L, "사원", Authority.MEMBER, "설명"));

        assertThat(summary.name()).isEqualTo("사원");
    }

    @Test
    @DisplayName("직급 목록을 구성원 수와 함께 조회한다")
    void listsPositionsWithMemberCount() {
        FakePositionRepository repository = new FakePositionRepository();
        Position position = repository.create(1L, "사원", Authority.MEMBER, "설명");
        FakeMemberQueryPort memberQueryPort = new FakeMemberQueryPort();
        memberQueryPort.addActiveMember(10L, position.id());
        memberQueryPort.addActiveMember(11L, position.id());

        List<PositionSummary> positions = service(repository, memberQueryPort).getPositions(1L);

        assertThat(positions).hasSize(1);
        assertThat(positions.get(0).memberCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("이름·권한·설명을 수정한다")
    void updatesPosition() {
        FakePositionRepository repository = new FakePositionRepository();
        Position position = repository.create(1L, "사원", Authority.MEMBER, "설명");

        PositionSummary summary = service(repository, new FakeMemberQueryPort())
                .update(new UpdatePositionCommand(1L, position.id(), "대리", Authority.LEADER, "새 설명"));

        assertThat(summary.name()).isEqualTo("대리");
        assertThat(summary.authority()).isEqualTo(Authority.LEADER);
        assertThat(summary.description()).isEqualTo("새 설명");
    }

    @Test
    @DisplayName("존재하지 않는 직급이면 404 로 거절한다")
    void rejectsUpdatingMissingPosition() {
        FakePositionRepository repository = new FakePositionRepository();

        assertThatThrownBy(() -> service(repository, new FakeMemberQueryPort())
                .update(new UpdatePositionCommand(1L, 999L, "대리", Authority.LEADER, "설명")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.POSITION_NOT_FOUND);
    }

    @Test
    @DisplayName("이름을 그대로 다시 저장해도 자기 자신과는 중복 처리하지 않는다")
    void allowsUpdatingToSameName() {
        FakePositionRepository repository = new FakePositionRepository();
        Position position = repository.create(1L, "사원", Authority.MEMBER, "설명");

        PositionSummary summary = service(repository, new FakeMemberQueryPort())
                .update(new UpdatePositionCommand(1L, position.id(), "사원", Authority.MEMBER, "새 설명"));

        assertThat(summary.description()).isEqualTo("새 설명");
    }

    @Test
    @DisplayName("바꾸려는 이름이 다른 직급과 겹치면 거절한다")
    void rejectsUpdatingToDuplicateName() {
        FakePositionRepository repository = new FakePositionRepository();
        Position sawon = repository.create(1L, "사원", Authority.MEMBER, "설명");
        repository.create(1L, "대리", Authority.MEMBER, "설명");

        assertThatThrownBy(() -> service(repository, new FakeMemberQueryPort())
                .update(new UpdatePositionCommand(1L, sawon.id(), "대리", Authority.MEMBER, "설명")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.POSITION_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("소속 구성원이 없으면 삭제된다")
    void deletesUnusedPosition() {
        FakePositionRepository repository = new FakePositionRepository();
        Position position = repository.create(1L, "사원", Authority.MEMBER, "설명");

        service(repository, new FakeMemberQueryPort()).delete(1L, position.id());

        assertThat(repository.findByIdAndCompanyId(position.id(), 1L)).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 직급이면 404 로 거절한다")
    void rejectsDeletingMissingPosition() {
        FakePositionRepository repository = new FakePositionRepository();

        assertThatThrownBy(() -> service(repository, new FakeMemberQueryPort()).delete(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.POSITION_NOT_FOUND);
    }

    @Test
    @DisplayName("해당 직급인 구성원이 있으면 삭제를 거절한다")
    void rejectsDeletingPositionInUse() {
        FakePositionRepository repository = new FakePositionRepository();
        Position position = repository.create(1L, "사원", Authority.MEMBER, "설명");
        FakeMemberQueryPort memberQueryPort = new FakeMemberQueryPort();
        memberQueryPort.addActiveMember(10L, position.id());

        assertThatThrownBy(() -> service(repository, memberQueryPort).delete(1L, position.id()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.POSITION_IN_USE);
        assertThat(repository.findByIdAndCompanyId(position.id(), 1L)).isPresent();
    }

    private PositionService service(PositionRepository repository, PositionMemberQueryPort memberQueryPort) {
        return new PositionService(repository, memberQueryPort);
    }

    /* ── 테스트 더블 ──────────────────────────────────────────────────── */

    static final class FakePositionRepository implements PositionRepository {

        private final List<Position> positions = new ArrayList<>();
        private long nextId = 1;

        @Override
        public List<Position> findByCompanyId(Long companyId) {
            return positions.stream().filter(p -> p.companyId().equals(companyId)).toList();
        }

        @Override
        public Optional<Position> findByIdAndCompanyId(Long id, Long companyId) {
            return positions.stream().filter(p -> p.id().equals(id) && p.companyId().equals(companyId)).findFirst();
        }

        @Override
        public Position create(Long companyId, String name, Authority authority, String description) {
            Position position = new Position(nextId++, companyId, name, authority, description);
            positions.add(position);
            return position;
        }

        @Override
        public void update(Long id, String name, Authority authority, String description) {
            positions.replaceAll(p -> p.id().equals(id)
                    ? new Position(p.id(), p.companyId(), name, authority, description)
                    : p);
        }

        @Override
        public void delete(Long id) {
            positions.removeIf(p -> p.id().equals(id));
        }

        @Override
        public boolean existsByCompanyIdAndName(Long companyId, String name) {
            return positions.stream().anyMatch(p -> p.companyId().equals(companyId) && p.name().equals(name));
        }
    }

    static final class FakeMemberQueryPort implements PositionMemberQueryPort {

        private final List<PositionMemberSummary> members = new ArrayList<>();

        void addActiveMember(Long memberId, Long positionId) {
            members.add(new PositionMemberSummary(memberId, positionId));
        }

        @Override
        public List<PositionMemberSummary> findActiveMembersByCompany(Long companyId) {
            return members;
        }

        @Override
        public boolean hasActiveMembers(Long positionId) {
            return members.stream().anyMatch(m -> positionId.equals(m.positionId()));
        }
    }
}
