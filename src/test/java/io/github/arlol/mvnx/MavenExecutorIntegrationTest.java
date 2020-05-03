package io.github.arlol.mvnx;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class MavenExecutorIntegrationTest {

	@Test
	public void testPrint() throws Exception {
		Path repository = TestPaths.get("maven-repository");
		// Overwrite repositories to ensure offline usage
		MavenExecutor.main(new String[] { "io.github.arlol:print:0.0.1", "--mainClass", "io.github.arlol.print.App",
				"--repositories", "http://localhost:62085", "--localRepository", repository.toString() });
	}

	@Test
	public void testException() throws Exception {
		Path repository = TestPaths.get("maven-repository");
		// Overwrite repositories to ensure offline usage
		assertThatThrownBy(() -> {
			MavenExecutor.main(new String[] { "io.github.arlol:exception:0.0.1", "--repositories",
					"http://localhost:62085", "--localRepository", repository.toString() });
		}).hasCause(new IllegalArgumentException("Expection ;)"));
	}

	@Test
	public void testWaitForPorts() throws Exception {
		Path repository = TestPaths.get("maven-repository");
		MavenExecutor.main(new String[] { "com.github.arlol:wait-for-ports:35b1ce08e2", "--saveToLocalRepository",
				"--localRepository", repository.toString(), "--", "wrongarg" });
	}

}
