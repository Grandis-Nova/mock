package com.grandis.nova.mockapi.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.grandis.nova.mockapi.registration.dto.RegisterRequest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * 같은 키 동시 요청을 <b>진짜 MySQL</b> 로 본다.
 *
 * <p>멱등 등록은 잠금 읽기와 중복 키(1062)에 기댄다. H2 는 둘 다 다르게 동작하므로 여기서 통과해야
 * 의미가 있다. 스키마는 {@code docs/schema.sql} 을 그대로 넣고({@code ddl-auto=validate} 로 엔티티와
 * 맞는지도 확인된다), 서버 격리 수준은 compose 와 같은 READ COMMITTED 다.
 *
 * <p>Docker 가 없으면 이 시험만 건너뛴다. 빌드는 막지 않는다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_READ_COMMITTED",
        "spring.datasource.hikari.maximum-pool-size=20"
})
class RegistrationConcurrencyTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("external_mock")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("docs/schema.sql"), "/docker-entrypoint-initdb.d/01-schema.sql")
            .withCommand(
                    "--transaction-isolation=READ-COMMITTED",
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_0900_ai_ci");

    private static final RegisterRequest REQUEST = new RegisterRequest(1001L, 12L, "SM-G999-256-BLK");

    /** 동시성 버그는 경합에서만 나온다. 한 번 통과는 증명이 아니다(명세: 최소 100회). */
    private static final int ROUNDS = 100;
    private static final int CONCURRENT = 10;

    /** MySQL 이 DATETIME(6) 을 글자로 내줄 때의 모양. */
    private static final DateTimeFormatter DB_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC);

    @Autowired
    private RegistrationService service;

    @Autowired
    private RegistrationRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoSpyBean
    private ExternalNumberGenerator numbers;

    @Test
    @DisplayName("커넥션 격리 수준이 READ COMMITTED 다 — 1062 를 정상 분기로 다루는 설계의 전제")
    void readCommitted() {
        assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class))
                .isEqualTo("READ-COMMITTED");
    }

    @Test
    @DisplayName("같은 키 동시 10건 × 100회 - 매번 등록 1건, 번호 1개, 나머지 9건은 재생")
    void sameKeyConcurrently() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String key = UUID.randomUUID().toString();
            List<RegisterResult> results = registerConcurrently(key);

            assertThat(results).as("round %d", round)
                    .filteredOn(result -> !result.replayed()).hasSize(1);
            assertThat(results).as("round %d", round)
                    .extracting(result -> result.registration().externalNumber())
                    .containsOnly(results.getFirst().registration().externalNumber());
            assertThat(repository.findById(key)).as("round %d", round).get()
                    .satisfies(saved -> {
                        assertThat(saved.isActive()).isTrue();
                        assertThat(saved.externalNumber())
                                .isEqualTo(results.getFirst().registration().externalNumber());
                    });
        }
    }

    private List<RegisterResult> registerConcurrently(String key) throws Exception {
        var start = new CountDownLatch(1);
        List<Future<RegisterResult>> futures = new ArrayList<>(CONCURRENT);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < CONCURRENT; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return service.register(key, REQUEST);
                }));
            }
            start.countDown();
            List<RegisterResult> results = new ArrayList<>(CONCURRENT);
            for (Future<RegisterResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    /**
     * 시각을 시간대 없는 값으로 두면 DB 에 밀린 값이 들어가는데, 앱 안에서는 읽을 때 거꾸로 되돌아가
     * 응답이 멀쩡하다. 그래서 응답이 아니라 <b>DB 에 적힌 글자</b>를 본다.
     *
     * <p>이 버그는 JVM 시간대가 UTC 가 아닐 때만 드러난다. 그래서 시험 JVM 은 한국 시간대로 돈다(build.gradle).
     */
    @Test
    @DisplayName("DB 에 저장된 시각 자체가 UTC 다 — 정합성 검사가 이 표를 직접 읽는다")
    void storesUtcInDatabase() {
        assertThat(ZoneId.systemDefault().getRules().getOffset(Instant.now()))
                .as("시험 JVM 이 UTC 면 이 시험은 아무것도 증명하지 못한다 — build.gradle 의 user.timezone 확인")
                .isNotEqualTo(ZoneOffset.UTC);

        Registration saved = service.register(UUID.randomUUID().toString(), REQUEST).registration();

        String stored = jdbc.queryForObject(
                "SELECT CAST(confirmed_at AS CHAR) FROM preorder_registrations WHERE external_key = ?",
                String.class, saved.externalKey());
        assertThat(stored).isEqualTo(DB_DATETIME.format(saved.confirmedAt()));
    }

    @Test
    @DisplayName("번호가 겹치면 MySQL 의 1062 를 받아 새 번호로 다시 시도한다")
    void numberCollisionOnMySql() {
        doReturn("R-19990101-0000000001")
                .doReturn("R-19990101-0000000001")
                .doReturn("R-19990101-0000000002")
                .when(numbers).next(any());

        assertThat(service.register(UUID.randomUUID().toString(), REQUEST).registration().externalNumber())
                .isEqualTo("R-19990101-0000000001");
        assertThat(service.register(UUID.randomUUID().toString(), REQUEST).registration().externalNumber())
                .isEqualTo("R-19990101-0000000002");
    }

    @Test
    @DisplayName("중복 키만 재시도 대상으로 알아본다 — CHECK 위반은 Mock 의 버그라 재시도하지 않는다")
    void recognizesOnlyDuplicateKey() {
        String key = UUID.randomUUID().toString();
        repository.saveAndFlush(Registration.cancelMarker(key, Instant.now()));

        assertThatThrownBy(() -> repository.saveAndFlush(Registration.cancelMarker(key, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(DuplicateKey.isCause(e)).isTrue());

        // ACTIVE 인데 번호가 없다 — ck_registration_active_fields 위반
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO preorder_registrations (external_key, status) VALUES (?, 'ACTIVE')",
                UUID.randomUUID().toString()))
                .isInstanceOf(DataAccessException.class)
                .satisfies(e -> assertThat(DuplicateKey.isCause(e)).isFalse());
    }
}
