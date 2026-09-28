package com.neo4flix.platform.common.test;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Neo4jGdsContainer implements AutoCloseable {

    private static final String USERNAME = "neo4j";
    private static final String PASSWORD = "test-password";
    private static final Duration MIGRATOR_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration OUTPUT_CAPTURE_TIMEOUT = Duration.ofSeconds(10);
    private static final String MIGRATION_HISTORY_QUERY =
            "MATCH (migration:__Neo4jMigration) RETURN count(migration) AS count";

    private final Neo4jContainer<?> container;
    private final Driver driver;

    private Neo4jGdsContainer(Neo4jContainer<?> container, Driver driver) {
        this.container = container;
        this.driver = driver;
    }

    public static Neo4jGdsContainer start() {
        Neo4jContainer<?> container = new Neo4jContainer<>(
                DockerImageName.parse("neo4j:2026.07.1-community"))
                .withAdminPassword(PASSWORD)
                .withEnv("NEO4J_PLUGINS", "[\"graph-data-science\"]")
                .waitingFor(Neo4jContainer.WAIT_FOR_BOLT)
                .withStartupTimeout(Duration.ofMinutes(3));
        container.start();

        Driver driver = GraphDatabase.driver(container.getBoltUrl(), AuthTokens.basic(USERNAME, PASSWORD));
        driver.verifyConnectivity();
        return new Neo4jGdsContainer(container, driver);
    }

    public List<Record> runCypher(String query, Map<String, Object> parameters) {
        return driver.executableQuery(query)
                .withParameters(parameters)
                .execute()
                .records();
    }

    public List<Record> runWriteCypher(String query, Map<String, Object> parameters) {
        try (Session session = driver.session()) {
            return session.executeWrite(transaction -> transaction.run(query, parameters).list());
        }
    }

    public Driver driver() {
        return driver;
    }

    public MigrationRun runMigrator(String mode) {
        long migrationHistoryBefore = migrationHistorySize();
        Path migratorJar = buildMigratorJar();
        ProcessBuilder processBuilder = new ProcessBuilder(javaExecutable(), "-jar", migratorJar.toString(), mode)
                .directory(repositoryRoot().toFile())
                .redirectErrorStream(true);
        Map<String, String> environment = processBuilder.environment();
        environment.put("NEO4J_URI", container.getBoltUrl());
        environment.put("NEO4J_USERNAME", USERNAME);
        environment.put("NEO4J_PASSWORD", PASSWORD);

        runProcess(processBuilder, "Database migrator");
        long migrationHistoryAfter = migrationHistorySize();
        return new MigrationRun(migrationHistoryAfter, migrationHistoryAfter - migrationHistoryBefore);
    }

    @Override
    public void close() {
        driver.close();
        container.stop();
    }

    private static Path buildMigratorJar() {
        Path root = repositoryRoot();
        // Build the executable in an isolated directory. The recommendation
        // tests consume the attached plain migrator JAR, so Maven keeps the
        // normal target artifact open on Windows while this nested build runs.
        Path target = root.resolve("database/migrator/target/it-migrator");
        try {
            ProcessBuilder build = new ProcessBuilder(
                    mavenWrapper(), "-pl", "database/migrator", "-am", "clean", "package", "-DskipTests",
                    "-Dmigrator.build.directory=" + target.toAbsolutePath())
                    .directory(root.toFile())
                    .redirectErrorStream(true);
            runProcess(build, "Database migrator build");
            try (var files = Files.list(target)) {
                return files.filter(path -> path.getFileName().toString()
                                .matches("database-migrator-.+\\.jar"))
                        .filter(path -> !path.getFileName().toString().endsWith("-plain.jar"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Database migrator JAR was not produced"));
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not build database migrator", exception);
        }
    }

    private long migrationHistorySize() {
        return runCypher(MIGRATION_HISTORY_QUERY, Map.of())
                .getFirst()
                .get("count")
                .asLong();
    }

    private static void runProcess(ProcessBuilder processBuilder, String description) {
        ExecutorService outputReader = Executors.newSingleThreadExecutor();
        try {
            Process process = processBuilder.start();
            Future<String> output = outputReader.submit(
                    () -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            if (!process.waitFor(MIGRATOR_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(OUTPUT_CAPTURE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                throw new IllegalStateException(description + " timed out:\n" + capturedOutput(output));
            }
            String diagnostics = capturedOutput(output);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(description + " failed:\n" + diagnostics);
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not start " + description.toLowerCase(), exception);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running " + description.toLowerCase(), exception);
        }
        finally {
            outputReader.shutdownNow();
        }
    }

    private static String capturedOutput(Future<String> output) {
        try {
            return output.get(OUTPUT_CAPTURE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "<interrupted while collecting diagnostics>";
        }
        catch (ExecutionException exception) {
            return "<could not collect diagnostics: " + exception.getCause().getMessage() + ">";
        }
        catch (TimeoutException exception) {
            return "<diagnostics not available before timeout>";
        }
    }

    public record MigrationRun(long migrationHistorySize, long addedMigrationHistoryEntries) {
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("mvnw"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Could not locate repository root");
        }
        return current;
    }

    private static String mavenWrapper() {
        return System.getProperty("os.name").startsWith("Windows")
                ? repositoryRoot().resolve("mvnw.cmd").toString()
                : "./mvnw";
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }
}
