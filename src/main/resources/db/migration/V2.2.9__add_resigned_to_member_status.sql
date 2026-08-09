-- =====================================================================
-- V2.2.9 : member.status 에 RESIGNED 추가                      [담당: 윤종호]
-- ---------------------------------------------------------------------
-- 배경:
--   V1 의 status 는 ACTIVE / ON_LEAVE / WAITING 셋뿐이라 퇴사를 표현할 값이 없다.
--   오프보딩 최종 승인은 deleted_at 을 찍는데, 그것만으로는
--   "퇴사해서 나간 사람"과 "잘못 만들어 지운 계정"이 구분되지 않는다.
--   구성원 목록의 상태 칸도 퇴사를 보여줘야 한다.
--
-- 확장이라 데이터 정리가 필요 없다:
--   ENUM 에 값을 더하는 것은 기존 행에 영향이 없다. 축소(V2.2.3·V2.2.5)와 달리
--   Error 1265 가 나지 않으므로 정리 파일을 앞에 두지 않았다.
--
-- 인계 메모:
--   오프보딩 최종 승인은 deleted_at 세팅 · status=RESIGNED 전환 ·
--   리프레시 토큰 폐기가 한 트랜잭션이어야 한다. 마지막이 빠지면 퇴직 처리된
--   계정이 남은 토큰으로 최대 30분간 계속 접근한다.
--   RefreshTokenStore.revokeAllByMember(memberId) 를 공개해 둔다.
--
-- 상태 라벨:
--   ACTIVE 재직 · ON_LEAVE 휴직 · WAITING 대기 · RESIGNED 퇴사
--   (WAITING 은 계정 발급이 즉시 ACTIVE 라 실제로는 생기지 않는다. 값만 남긴다)
-- =====================================================================

ALTER TABLE `member`
    MODIFY COLUMN `status` ENUM('ACTIVE', 'ON_LEAVE', 'WAITING', 'RESIGNED')
    NOT NULL DEFAULT 'ACTIVE'
    COMMENT 'ACTIVE=재직 / ON_LEAVE=휴직 / WAITING=승인 대기 / RESIGNED=퇴사';
