# Flyway Migration 운영 규칙 (module06)

> 대상: 백엔드 6인 협업 (Java 17 · Spring Boot · MySQL 8 · Flyway)
> 목적: 담당자별 버전 분리 + out-of-order 허용 + 운영 배포 안정성
> 기준: 첨부 ERD(Module06 Ver 1.1.0)를 **V1 baseline**으로 고정, 이후 담당자별 V2.N.x

---

## 1. 기본 정책

- 초기 전체 스키마는 **`V1__init_schema.sql`** 하나에 담는다(baseline).
- 이후 모든 신규 DB 변경은 **담당자별 V2.N.x 영역**에서 증분으로만 추가한다.
- **baseline 처리 (확정값): `spring.flyway.baseline-on-migrate=false`**
  - 이 프로젝트에는 *마이그레이션 이력 없이 스키마만 존재하는* 레거시 DB가 없다. 모든 환경이 빈 DB에서 `V1`부터 시작한다.
  - ⚠️ 켜두면 **테이블이 일부만 있는 DB를 "V1 완료"로 등록하고 `V1`을 건너뛴다.** Flyway는 기존 테이블이 `V1`과 일치하는지 검증하지 않으므로, 깨진 스키마가 조용히 통과한다.
  - 실제 레거시 DB를 인계받는 경우에만, **스키마 대조(`SHOW CREATE TABLE` / schema diff) 후 승인**을 거쳐 그 배포에 한해 일시적으로 켠다.
  - 로컬이 꼬였을 때 `flyway_schema_history`만 지우지 말 것 → **9번(DB 전체 drop/recreate)** 을 따른다.

---

## 2. 담당자별 버전 영역

각 담당자는 **배정받은 영역만** 사용한다. (버전 = `V2.{담당자}.{순번}`)

| 담당자 | 버전 영역 | 예시 |
| --- | --- | --- |
| 박종준 | `V2.1.x` | `V2.1.1__...sql` |
| 윤종호 | `V2.2.x` | `V2.2.1__...sql` |
| 모성진 | `V2.3.x` | `V2.3.1__...sql` |
| 김현지 | `V2.4.x` | `V2.4.1__...sql` |
| 이태연 | `V2.5.x` | `V2.5.1__...sql` |
| 김민섭 | `V2.6.x` | `V2.6.1__...sql` |

> Flyway는 동일 버전 번호를 허용하지 않으므로, 영역 분리가 파일명/버전 충돌을 원천 차단한다.
> 순번은 자기 레인 안에서 1부터 증가시킨다 (`V2.2.1`, `V2.2.2`, ...).

---

## 3. Migration 파일 수정 금지

이미 Push/Merge된 Migration 파일은 **수정하지 않는다.**
Flyway는 적용 시 checksum을 `flyway_schema_history`에 저장하며, 적용된 파일을 고치면 checksum 불일치로 `validate`가 실패한다(팀 규칙이 아니라 Flyway의 물리적 제약).

- ❌ 병합된 `V2.1.1__...sql` 내용 변경
- ✅ 새 파일 `V2.1.2__...sql`로 보완

## 4. Migration 파일 삭제 금지

- ❌ 병합된 마이그레이션 파일 삭제
- ✅ 잘못된 변경은 새 마이그레이션으로 되돌리기(revert)

---

## 5. Out-Of-Order (개발만 허용)

`spring.flyway.out-of-order` — **개발 `true` / 운영 `false`** (`FLYWAY_OUT_OF_ORDER` 환경변수로 제어).

이미 적용된 최신 버전보다 **낮은** 미적용 마이그레이션이 뒤늦게 들어와도 무시하지 않고 적용해 준다(꺼져 있으면 무시된다). 각 파일은 DB당 1회만 실행된다.

**⚠️ 실행 순서를 재배치하는 기능이 아니다.** 이미 지나간 이력은 되돌리지 않으므로, 뒤늦게 온 낮은 버전은 **그 시점에**, 즉 더 높은 버전 뒤에 실행된다.

```text
적용 순서:  V2.1.1 → V2.2.1 → V2.1.2 (뒤늦게 병합, 지금 실행됨) → V2.3.1
버전 순서:  V2.1.1 → V2.1.2 → V2.2.1 → V2.3.1                  ← 이렇게 되지 않는다
```

> **그래서 지켜야 할 규칙: 낮은 버전 마이그레이션이 높은 버전의 변경에 의존하면 안 된다.**
> 예) `V2.1.2`가 `V2.2.1`이 추가한 컬럼을 참조하면, 위 순서에서는 우연히 성공하지만
> 빈 DB에 처음부터 적용할 때는 버전 순서대로 실행되므로 **실패한다.**
> 새 컬럼을 참조하는 변경은 반드시 자기 버전보다 **낮은** 버전에만 의존하도록 작성한다.

## 6. DB 변경은 반드시 Migration으로

- ❌ DB에 직접 SQL 실행 후 종료
- ✅ 파일 생성 후 Flyway로 적용

## 7. 공용 테이블 변경 시 사전 공유

**module06 공용 테이블** = `company`, `member`, `team`, `sub_team`, `job_position`, `project`, `meeting`, `action` 등 테넌트/조직 핵심.
이들 변경 시 반드시 팀 채널에 사전 공유(out-of-order 환경에서 담당자 간 의존성 충돌 방어선).

```text
[DB 변경 예정]
파일명: V2.2.1__add_last_login_at_to_member.sql
대상: member
변경 내용: last_login_at 컬럼 추가
영향 범위: 인증 / 마이페이지
```

---

## 8. 방어적 작성 원칙

### 8-1. ⚠ MySQL은 `ADD COLUMN IF NOT EXISTS`를 지원하지 않는다 (정정)

`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`는 **MariaDB 전용 문법**이다.
**MySQL 8에서는 문법 오류로 즉시 실패**한다. (이전 규칙 문서의 권장 예시는 MySQL에서 쓸 수 없다.)

MySQL에서의 원칙:
- **수동 DB 변경을 하지 않는다** — Flyway가 각 마이그레이션을 DB당 1회만 실행하므로, 수동 개입만 없으면 멱등성 방어 코드는 애초에 필요 없다.
- 로컬 DB가 꼬이면 방어 코드로 덮지 말고 **9번(재생성)**으로 초기화한다.
- 정말로 조건부 DDL이 필요하면 `INFORMATION_SCHEMA` 조회 + 동적 SQL(프로시저)로 감싼다. (남용 금지)

### 8-2. MySQL은 마이그레이션 단위 롤백이 안 된다

용어를 구분해야 한다. **둘은 다르다.**

| | MySQL 8.0 InnoDB | 의미 |
|---|---|---|
| **atomic DDL** | ✅ 지원 | `ALTER TABLE` **한 문장**은 성공 아니면 실패. 반만 적용된 컬럼 같은 건 없다 |
| **transactional DDL** | ❌ 미지원 | **여러 DDL을 한 트랜잭션으로 묶어 통째로 롤백**하는 것. PostgreSQL은 되지만 MySQL은 안 된다 |

DDL은 실행 시 진행 중인 트랜잭션을 **암묵적으로 커밋**시킨다. 따라서 한 파일에 DDL이 여러 개면 3번째에서 실패해도 **앞의 두 개는 이미 커밋돼 되돌릴 수 없다.** 마이그레이션은 실패로 기록되는데 스키마는 반쯤 바뀐 상태로 남는다.

- **1 Migration 파일 = 1 논리 변경.** 여러 테이블/여러 DDL은 파일을 나눈다.
- **예외**: `V1__init_schema.sql`(baseline)은 초기 전체 스키마라 다수 DDL을 한 파일에 담는다. baseline 이후에만 1파일-1변경을 강제한다.

---

## 9. 로컬 DB 오류 발생 시

1. 로컬 DB 삭제
2. Flyway 초기화
3. 애플리케이션 재실행
4. **`V1` → `V2.x`** 순서로 재적용

`flyway_schema_history` 직접 수정은 **금지**.

## 10. 작성 규칙

- 파일명: `V{버전}__{설명}.sql` (설명은 영문 snake_case)
  예) `V2.4.2__add_capacity_index_to_meeting_room.sql`
- 버전은 배정받은 담당자 영역을 지킨다.
- 뷰/프로시저처럼 **매번 통째로 다시 만들어도 되는** 대상은 `R__` (Repeatable) 사용 검토.
- ⚠️ **`R__` 는 checksum 이 바뀔 때마다 다시 실행된다.**
  - ❌ INSERT 위주 시드를 `R__` 로 두고 나중에 내용을 수정하면 **중복 데이터 / PK·UNIQUE 충돌**이 난다.
  - ✅ **일회성 시드는 versioned(`V2.N.x`)로** 작성한다.
  - ✅ `R__` 에는 `CREATE OR REPLACE VIEW`, `INSERT ... ON DUPLICATE KEY UPDATE` 같은 **몇 번 실행해도 결과가 같은 SQL만** 넣는다.

## 11. 운영 환경 원칙

- 운영 DB 기존 Migration 수정 금지 → 문제는 새 Migration으로 보완(긴급 수정도 파일로 남긴다).
- 운영 반영 전 CI/스테이징에서 `flyway validate` + `flyway migrate` 확인.
- **머지 순서 = 배포 순서** 강제. out-of-order는 개발 편의용이다.
- **운영 환경변수 (SSM Parameter Store `/itta/spring/`)** — 문서상의 원칙을 설정으로도 강제한다.

  | 환경변수 | 운영 값 | 이유 |
  | --- | --- | --- |
  | `FLYWAY_OUT_OF_ORDER` | `false` | 머지 순서 = 배포 순서 강제. 낮은 버전이 뒤늦게 끼어드는 것을 차단 |
  | `JPA_DDL_AUTO` | `validate` | 엔티티↔스키마 불일치 시 부팅 차단 |

  > 설정하지 않으면 공통 기본값(`out-of-order=true`)이 운영에도 그대로 적용된다. **반드시 명시할 것.**
  > `baseline-on-migrate`는 전 환경 `false`이며, 레거시 DB 인계 시에만 스키마 대조·승인 후 일시적으로 켠다(1번 참조).

---

## 최종 체크리스트

- [ ] Migration 파일 수정 금지 (checksum 불일치 → validate 실패)
- [ ] Migration 파일 삭제 금지
- [ ] DB 변경은 반드시 Migration으로
- [ ] 담당 버전 영역(V2.N.x)만 사용
- [ ] 공용 테이블 변경 시 사전 공유
- [ ] MySQL은 `IF NOT EXISTS` 컬럼 문법 불가 → 수동 DB 변경 자체를 금지
- [ ] 1파일 1논리변경 (baseline V1만 예외)
- [ ] 낮은 버전이 높은 버전의 변경에 의존하지 않게 작성 (out-of-order)
- [ ] baseline-on-migrate=false 유지 (레거시 DB 인계 시에만 대조·승인 후 일시 허용)
- [ ] 충돌 시 로컬 DB 전체 drop/recreate (히스토리 테이블만 지우지 않기)
- [ ] 운영 반영 전 validate + migrate 검증 (CI: `migration-check` job에서 자동 검증)
- [ ] 머지 순서 = 배포 순서 유지 (운영 `FLYWAY_OUT_OF_ORDER=false`)
