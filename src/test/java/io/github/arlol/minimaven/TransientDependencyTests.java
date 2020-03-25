package io.github.arlol.minimaven;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class TransientDependencyTests {

	@Test
	public void test() throws Exception {
		Path localRepository = TestPaths.get("maven-repository");
		Project project = ClassLoaderExperiment.project(localRepository,
				ClassLoaderExperiment.pomPath("org.slf4j:slf4j-nop:1.7.30"));
		assertThat(project).isNotNull();
		assertThat(project.dependencies).isNotEmpty();
		assertThat(project.dependencies).contains(
				DependencyBuilder.builder().groupId("org.slf4j").artifactId("slf4j-api").version("1.7.30").build());
		assertThat(project.parent.dependencies).contains(
				DependencyBuilder.builder().groupId("junit").artifactId("junit").version("4.12").scope("test").build());
	}

}
