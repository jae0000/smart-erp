package com.smarterp;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("oracle")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OracleProductApiTest {
    private static final String PATH = "/api/products";
    private static final String TEST_PASSWORD = "Qa1" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

    @Container
    static final GenericContainer<?> oracle = new GenericContainer<>("container-registry.oracle.com/database/free:latest-lite")
            .withExposedPorts(1521)
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../database/product.sql").toAbsolutePath()), "/tmp/product.sql")
            .waitingFor(Wait.forLogMessage(".*DATABASE IS READY TO USE!.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(6)));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:oracle:thin:@//" + oracle.getHost()
                + ":" + oracle.getMappedPort(1521) + "/FREEPDB1");
        properties.add("spring.datasource.username", () -> "SMARTERP");
        properties.add("spring.datasource.password", () -> TEST_PASSWORD);
    }

    @BeforeAll
    static void prepareDisposableSchema() throws Exception {
        runSqlPlus("""
                ALTER SESSION SET CONTAINER = FREEPDB1;
                ALTER SESSION SET DB_CREATE_FILE_DEST = '/opt/oracle/oradata';
                CREATE TABLESPACE SMARTERP_DATA DATAFILE SIZE 10M;
                CREATE USER SMARTERP IDENTIFIED BY "%s" DEFAULT TABLESPACE SMARTERP_DATA QUOTA 10M ON SMARTERP_DATA;
                GRANT CREATE SESSION, CREATE TABLE TO SMARTERP;
                ALTER SESSION SET CURRENT_SCHEMA = SMARTERP;
                @/tmp/product.sql
                """.formatted(TEST_PASSWORD));
    }

    private static void runSqlPlus(String sql) throws Exception {
        // The script runs only in the temporary container owned by this test.
        String script = "sqlplus -s -L / as sysdba <<'SQL'\nWHENEVER OSERROR EXIT FAILURE\n"
                + "WHENEVER SQLERROR EXIT FAILURE ROLLBACK\n" + sql + "\nEXIT SUCCESS\nSQL";
        var result = oracle.execInContainer("bash", "-c", script);
        assertThat(result.getExitCode())
                .as("%s", (result.getStdout() + result.getStderr()).replace(TEST_PASSWORD, "***"))
                .isZero();
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @Test
    void seedRepairsTheKnownEncodingIssueAndPreservesEditedProducts() throws Exception {
        String rerun = """
                ALTER SESSION SET CONTAINER = FREEPDB1;
                ALTER SESSION SET CURRENT_SCHEMA = SMARTERP;
                @/tmp/product.sql
                """;
        String nameQuery = "SELECT product_name FROM product WHERE product_code = 'P001'";
        try {
            String damagedName = "\uFFFD".repeat(6) + " " + "\uFFFD".repeat(9);
            jdbc.update("UPDATE product SET product_name = ?, stock_qty = 7 WHERE product_code = 'P001'", damagedName);
            runSqlPlus(rerun);
            assertThat(jdbc.queryForObject(nameQuery, String.class)).isEqualTo("무선 마우스");
            assertThat(jdbc.queryForObject("SELECT stock_qty FROM product WHERE product_code = 'P001'", Integer.class))
                    .isEqualTo(7);

            jdbc.update("UPDATE product SET product_name = ? WHERE product_code = 'P001'", "직접 수정한 이름");
            runSqlPlus(rerun);
            assertThat(jdbc.queryForObject(nameQuery, String.class)).isEqualTo("직접 수정한 이름");
        } finally {
            jdbc.update("UPDATE product SET product_name = ?, stock_qty = 12 WHERE product_code = 'P001'", "무선 마우스");
        }
    }

    @Test
    void servesAndPersistsProductsInOracle() {
        var seed = http.getForEntity(PATH, JsonNode.class);
        assertThat(seed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seed.getBody().required("products").required(0).required("productName").textValue())
                .isEqualTo("무선 마우스");
        assertThat(jdbc.queryForObject("SELECT USER FROM dual", String.class)).isEqualTo("SMARTERP");

        var input = new ProductInput("IT-001", "연동 확인", new BigDecimal("59.40"), 3);
        assertThat(http.postForEntity(PATH, input, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity(PATH, input, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        long id = jdbc.queryForObject("SELECT product_id FROM product WHERE product_code = ?", Long.class, "IT-001");
        var edit = new ProductInput("IT-001", "수정한 상품", new BigDecimal("27.95"), 0);
        var changed = http.exchange(PATH + "/{id}", HttpMethod.PUT, new HttpEntity<>(edit), JsonNode.class, id);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT price FROM product WHERE product_id = ?", BigDecimal.class, id))
                .isEqualByComparingTo("27.95");

        assertThat(http.exchange(PATH + "/{id}", HttpMethod.DELETE, null, JsonNode.class, id).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.exchange(PATH + "/{id}", HttpMethod.DELETE, null, JsonNode.class, id).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product WHERE product_id = ?", Integer.class, id)).isZero();
    }
}
