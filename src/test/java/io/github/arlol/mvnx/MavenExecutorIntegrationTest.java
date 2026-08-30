package io.github.arlol.mvnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
public class MavenExecutorIntegrationTest {

	@Test
	public void testPrint(CapturedOutput output) throws Exception {
		Path repository = TestPaths.get("maven-repository");
		// Overwrite repositories to ensure offline usage
		MavenExecutor.main(
				new String[] { "io.github.arlol:print:0.0.1", "--mainClass",
						"io.github.arlol.print.App", "--repositories",
						"http://localhost:62085", "--localRepository",
						repository.toString() }
		);
		assertThat(output).contains("Hello, World!");
	}

	@Test
	public void testException() throws Exception {
		Path repository = TestPaths.get("maven-repository");
		// Overwrite repositories to ensure offline usage
		assertThatThrownBy(() -> {
			MavenExecutor.main(
					new String[] { "io.github.arlol:exception:0.0.1",
							"--repositories", "http://localhost:62085",
							"--localRepository", repository.toString() }
			);
		}).hasCause(new IllegalArgumentException("Expection ;)"));
	}

	@Test
	public void testWaitForPorts(CapturedOutput output) throws Exception {
		Path repository = TestPaths.get("maven-repository");
		MavenExecutor.main(
				new String[] { "com.github.arlol:wait-for-ports:35b1ce08e2",
						"--saveToLocalRepository", "--localRepository",
						repository.toString(), "--", "wrongarg" }
		);
		assertThat(output).contains("Testing wrongarg : null not supported");
		assertThat(
				repository.resolve(
						"com/github/arlol/wait-for-ports/35b1ce08e2/wait-for-ports-35b1ce08e2.jar"
				)
		).exists();
	}

}
