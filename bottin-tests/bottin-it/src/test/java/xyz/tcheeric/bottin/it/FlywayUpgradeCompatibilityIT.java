package xyz.tcheeric.bottin.it;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import xyz.tcheeric.bottin.persistence.entity.Nip05RecordEntity;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT-07: a database migrated by the Flyway that production ran before this upgrade
 * still validates under the Flyway this build ships.
 *
 * <p>Flyway refuses to start an application whose schema history disagrees with the
 * migrations it resolves: a changed checksum, a description it reads differently, a
 * history row type it no longer recognises. None of that can be seen from a fresh
 * database, which is what every other test starts from, because there the history is
 * written by the same Flyway that then reads it. Only a history written by the old
 * version exercises the upgrade a live deployment actually goes through.
 *
 * <p>The old Flyway therefore runs for real, from its own jars in an isolated class
 * loader (copied by the build into {@code legacy.flyway.dir}), against PostgreSQL, the
 * engine production uses. The current Flyway then reads what it wrote.
 */
@Testcontainers
class FlywayUpgradeCompatibilityIT {

    private static final String LEGACY_FLYWAY_DIR_PROPERTY = "legacy.flyway.dir";
    private static final String MIGRATIONS = "classpath:db/migration";

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("bottin_upgrade")
                    .withUsername("bottin")
                    .withPassword("bottin");

    private static URLClassLoader legacyFlywayLoader;

    @BeforeAll
    static void loadLegacyFlyway() throws IOException {
        legacyFlywayLoader = new URLClassLoader(legacyClasspath(), ClassLoader.getPlatformClassLoader());
    }

    @AfterAll
    static void closeLegacyFlyway() throws IOException {
        legacyFlywayLoader.close();
    }

    /**
     * Tests that every migration the old Flyway applied validates under the new one,
     * so the first start after the upgrade is not refused for a checksum mismatch.
     */
    @Test
    void shouldValidateAHistoryWrittenByTheLegacyFlyway() throws Exception {
        // Arrange: the schema as production has it, written by the old Flyway
        LegacyMigration legacy = migrateWithLegacyFlyway();

        // Act: the current Flyway validates what it finds
        ValidateResult validation = currentFlyway().validateWithResult();

        // Assert: two different versions, and the newer accepts the older's history
        assertThat(legacy.flywayVersion()).isNotEqualTo(validation.flywayVersion);
        assertThat(legacy.migrationsExecuted()).isEqualTo(appliedCount());
        assertThat(validation.invalidMigrations).isEmpty();
        assertThat(validation.validationSuccessful).isTrue();
    }

    /**
     * Tests that the current Flyway finds nothing left to do on an upgraded database:
     * it neither re-applies a migration it fails to recognise nor rewrites the history.
     */
    @Test
    void shouldApplyNothingOnTopOfALegacyHistory() throws Exception {
        // Arrange: the old Flyway's history, and what it recorded
        migrateWithLegacyFlyway();
        List<String> recordedBefore = history();

        // Act: the current Flyway migrates, as the application does at start-up
        MigrateResult result = currentFlyway().migrate();

        // Assert: nothing applied, and the history is untouched
        assertThat(result.migrationsExecuted).isZero();
        assertThat(history()).isEqualTo(recordedBefore);
    }

    private static LegacyMigration migrateWithLegacyFlyway() throws Exception {
        currentFlyway().clean();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(legacyFlywayLoader);
        try {
            Class<?> flywayType = legacyFlywayLoader.loadClass("org.flywaydb.core.Flyway");
            Object configuration = flywayType.getMethod("configure", ClassLoader.class)
                    .invoke(null, legacyFlywayLoader);
            configuration = invoke(configuration, "dataSource",
                    new Class<?>[]{String.class, String.class, String.class},
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            configuration = invoke(configuration, "locations",
                    new Class<?>[]{String[].class}, (Object) new String[]{MIGRATIONS});
            configuration = invoke(configuration, "cleanDisabled",
                    new Class<?>[]{boolean.class}, false);
            Object flyway = invoke(configuration, "load", new Class<?>[0]);
            Object result = invoke(flyway, "migrate", new Class<?>[0]);
            return new LegacyMigration(
                    (String) result.getClass().getField("flywayVersion").get(result),
                    (int) result.getClass().getField("migrationsExecuted").get(result));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static Flyway currentFlyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(MIGRATIONS)
                .cleanDisabled(false)
                .load();
    }

    private static int appliedCount() {
        return currentFlyway().info().applied().length;
    }

    private static List<String> history() {
        return Arrays.stream(currentFlyway().info().applied())
                .map(FlywayUpgradeCompatibilityIT::describe)
                .toList();
    }

    private static String describe(MigrationInfo migration) {
        return migration.getVersion() + " " + migration.getDescription() + " " + migration.getChecksum()
                + " " + migration.getInstalledRank() + " " + migration.getInstalledOn();
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name, types);
        return method.invoke(target, arguments);
    }

    /**
     * The old Flyway's jars, plus the module holding the migrations. The parent is the
     * platform loader, so nothing on the test class path, the current Flyway least of
     * all, can stand in for a class the old one was meant to load.
     */
    private static URL[] legacyClasspath() throws IOException {
        String directory = System.getProperty(LEGACY_FLYWAY_DIR_PROPERTY);
        assertThat(directory)
                .as("system property %s, set by the build to the copied legacy Flyway jars",
                        LEGACY_FLYWAY_DIR_PROPERTY)
                .isNotBlank();

        List<URL> urls = new ArrayList<>();
        try (Stream<Path> jars = Files.list(Path.of(directory))) {
            for (Path jar : jars.filter(path -> path.toString().endsWith(".jar")).toList()) {
                urls.add(jar.toUri().toURL());
            }
        }
        assertThat(urls).as("legacy Flyway jars in %s", directory).isNotEmpty();
        urls.add(Nip05RecordEntity.class.getProtectionDomain().getCodeSource().getLocation());
        return urls.toArray(URL[]::new);
    }

    private record LegacyMigration(String flywayVersion, int migrationsExecuted) {
    }
}
