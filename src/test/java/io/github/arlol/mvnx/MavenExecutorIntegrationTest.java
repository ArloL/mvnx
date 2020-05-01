package io.github.arlol.mvnx;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class MavenExecutorIntegrationTest {

	@Test
	public void testPrint() throws Exception {
		Path repository = TestPaths.get("maven-repository");
		// Overwrite repositories to ensure offline usage
		MavenExecutor.main(new String[] { "io.github.ArloL:print:0.0.1", "--repositories", "http://localhost:62085",
				"--localRepository", repository.toString() });
	}

	@Test
	public void testException() throws Exception {
		Path repository = TestPaths.get("maven-repository");
		// Overwrite repositories to ensure offline usage
		assertThatThrownBy(() -> {
			MavenExecutor.main(new String[] { "io.github.ArloL:exception:0.0.1", "--repositories",
					"http://localhost:62085", "--localRepository", repository.toString() });
		}).hasCause(new IllegalArgumentException("Expection ;)"));
	}

}
