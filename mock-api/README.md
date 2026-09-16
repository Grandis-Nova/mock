# mock-api

외부 예약 시스템(통신사 예약 서버) 역할을 대신하는 Mock 서버.
요구사항 명세서 5.4 의 계약을 구현한다.

> **현재 상태: 패키지 구조만 잡힌 상태다.** 아래 API 는 구현할 계약이며 아직 코드가 없다.

본 서비스(preorder-api)와 **HTTP 로만** 통신하며 코드·DB 를 공유하지 않는다.
정합성 배치(6장)가 "두 시스템의 독립된 기록을 대조"하는 기능이라, 저장소를 공유하면
그 기능 자체가 성립하지 않기 때문이다.

## 실행

```bash
docker compose up -d mysql       # 루트에서. reservation_mock database 가 만들어진다
./gradlew :mock-api:bootRun      # http://localhost:8081
```

저장소는 MySQL `reservation_mock` database 다. preorder 와 **같은 인스턴스의 다른 database** 이며
cross-database 조회나 물리 FK 를 두지 않는다. 재기동해도 등록 기록이 유지된다(5.4 기록 보존).

지연·실패율 설정은 테이블에 두지 않으므로 **재기동하면 기본값(500ms / 5%)으로 돌아간다.**
요구사항이 요구하는 것은 "재기동 없이 변경"(FR-M-05)이지 "재기동 후 유지"가 아니고,
시연은 매번 같은 초기 상태에서 시작하는 편이 낫기 때문이다.

## API

### 예약 등록 (멱등)

```bash
curl -X POST localhost:8081/reservations \
  -H 'Idempotency-Key: order-1' -H 'Content-Type: application/json' \
  -d '{"memberId":1,"modelId":10,"optionId":100,"quantity":1}'
```

| 상황 | 응답 |
| --- | --- |
| 최초 요청 | `201` + 새 `externalReservationNo` |
| 같은 키 · 같은 내용 | `201` + **같은** `externalReservationNo` (새 등록을 만들지 않음) |
| 같은 키 · 다른 내용 | `409 IDEMPOTENCY_KEY_CONFLICT` |

동일 내용 판정은 JSON 문자열이 아니라 업무 필드(`memberId:modelId:optionId:quantity`)로 한다(5.3).

### 조회

```bash
curl localhost:8081/reservations/EXT-00000001     # 단건
curl localhost:8081/reservations                  # 전체 (취소분 포함, status 로 구분)
```

전체 조회는 정합성 배치가 외부 등록을 빠짐없이 훑기 위한 것이다(FR-C-07/08).

### 취소 (멱등)

```bash
curl -X POST localhost:8081/reservations/EXT-00000001/cancel
```

미등록 예약번호도, 이미 취소된 건도 `200` 으로 응답한다. 통신 장애로 같은 대상에
취소를 반복해도 중복 효과가 생기지 않는다(5.4).

### 지연·실패율 변경 (시연용)

```bash
curl localhost:8081/admin/chaos
curl -X PUT localhost:8081/admin/chaos \
  -H 'Content-Type: application/json' -d '{"delayMillis":3000,"failureRatePercent":50}'
```

서버 재기동 없이 즉시 적용되며, 응답으로 **실제 적용값**을 돌려준다(FR-M-05 수락 기준).
`/admin` 경로에는 지연·실패가 주입되지 않으므로 실패율 100% 상태에서도 되돌릴 수 있다.

기본값은 `application.yml` 의 `mock.chaos` (500ms / 5%).

## 구조

클래스 15개 안쪽이라 계층별 패키지를 두지 않고 평탄하게 간다.

```
com.grandis.nova.mockapi/
├── global/
│   ├── chaos/      ChaosSettings(현재 설정) + ChaosFilter(지연·실패 주입)
│   └── error/      예외 → 응답 매핑
├── registration/   Reservation 등록·조회·취소, 멱등 판정, 채번
└── admin/          지연·실패율 조회·변경
```

지연·실패 주입을 필터에 둔 이유는 업무 코드에 `Thread.sleep` 과 랜덤 실패가 섞이지 않게
하기 위해서다. 대신 **조회·취소에도 지연이 걸린다** — 정합성 배치의 전체 조회가 느려지는 게
거슬리면 `ChaosFilter.shouldNotFilter` 에서 경로를 조정하면 된다.

## 테스트

```bash
./gradlew :mock-api:test
```

테스트에서는 `src/test/resources/application.yml` 이 H2 인메모리로,
지연·실패율을 0 으로 덮어쓴다.

> **주의:** H2 는 컨텍스트 로딩 확인용이다. 멱등 등록의 동시성은 유니크 제약에 의존하므로
> H2 에서 통과해도 MySQL 에서 통과한다는 보장이 없다. 동시성 테스트는 Testcontainers MySQL 로
> 옮겨야 한다(미결정).

구현 시 최소한 다음 4가지는 검증해야 한다.

- 같은 키·같은 내용의 재요청이 같은 예약번호를 반환하는가 (5.4 멱등 등록)
- 같은 키·다른 내용을 409 로 거절하는가 (5.3)
- 취소를 반복해도, 미등록 예약번호를 취소해도 성공으로 처리하는가 (5.4)
- 실패율 100% 에서도 관리자 API 로 설정을 되돌릴 수 있는가 (FR-M-05)
