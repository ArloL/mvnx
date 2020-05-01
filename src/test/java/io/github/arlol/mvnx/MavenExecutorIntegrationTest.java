package io.github.arlol.mvnx;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class MavenExecutorIntegrationTest {

	@Test
	public void test() throws Exception {
		// Overwrite repositories to ensure offline usage
		Path repository = TestPaths.get("maven-repository");
		MavenExecutor.main(new String[] { "io.github.ArloL:print:0.0.1", "--repositories", "http://localhost:62085",
				"--localRepository", repository.toString() });
	}

}
