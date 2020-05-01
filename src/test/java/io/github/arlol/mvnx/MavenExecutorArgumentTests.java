package io.github.arlol.mvnx;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

public class MavenExecutorArgumentTests {

	@Test
	public void testEmptyArguments() {
		assertThatThrownBy(() -> {
			parseArguments(emptyList());
		}).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Missing artifact identifier");
	}

	@Test
	public void testIdentifier() {
		MavenExecutor executor = parseArguments(List.of("com.github.ArloL:newlinechecker:133576b455"));
		assertThat(executor.groupId).isEqualTo("com.github.ArloL");
		assertThat(executor.artifactId).isEqualTo("newlinechecker");
		assertThat(executor.version).isEqualTo("133576b455");
	}

	@Test
	public void testMainClass() {
		String mainClass = "someunknown.MainClass";
		MavenExecutor executor = parseArguments(
				List.of("com.github.ArloL:newlinechecker:133576b455", "--mainClass", mainClass));
		assertThat(executor.mainClass).isEqualTo(mainClass);
	}

	@Test
	public void testRepositories() {
		MavenExecutor executor = parseArguments(
				List.of("com.github.ArloL:newlinechecker:133576b455", "--repositories", "https://jitpack.io/"));
		assertThat(executor.repositories).containsExactly("https://jitpack.io/");
	}

	@Test
	public void testSettings() {
		String settings = "/home/runner/settings.xml";
		MavenExecutor executor = parseArguments(
				List.of("com.github.ArloL:newlinechecker:133576b455", "--settings", settings));
		assertThat(executor.settingsXml).isEqualTo(Paths.get(settings));
	}

	@Test
	public void testPassthroughArguments() {
		MavenExecutor executor = parseArguments(
				List.of("com.github.ArloL:newlinechecker:133576b455", "--", "rest-of-arguments"));
		assertThat(executor.passthroughArguments).containsExactly("rest-of-arguments");
	}

	private MavenExecutor parseArguments(List<String> arguments) {
		return new MavenExecutor().parseArguments(arguments.toArray(new String[0]));
	}

}
