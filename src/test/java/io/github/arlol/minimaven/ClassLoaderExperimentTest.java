package io.github.arlol.minimaven;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;

public class ClassLoaderExperimentTest {

	@Test
	public void testUserHome() {
		Path userHome = ClassLoaderExperiment.userHome();
		assertThat(userHome).exists();
	}

	@Test
	public void testUserHomeM2() {
		Path userHomeM2 = ClassLoaderExperiment.userHomeM2(Paths.get("/root"));
		assertThat(userHomeM2).isEqualByComparingTo(Paths.get("/root/.m2"));
	}

	@Test
	public void testSettingsXml() {
		Path settingsXml = ClassLoaderExperiment.settingsXml(Paths.get("/root/.m2"));
		assertThat(settingsXml).isEqualByComparingTo(Paths.get("/root/.m2/settings.xml"));
	}

	@Test
	public void testLocalRepositoryNoSettingsXml() throws Exception {
		Path settingsXml = Paths.get("THIS-FILE-DOES-NOT-EXIST.xml");
		Path localRepository = ClassLoaderExperiment.localRepository(Paths.get("/root/.m2"), settingsXml);
		assertThat(localRepository).isEqualByComparingTo(Paths.get("/root/.m2/repository"));
	}

	@Test
	public void testLocalRepositoryEmpty() throws Exception {
		Path settingsXml = TestPaths.get("maven-settings-local-repository-empty.xml");
		Path localRepository = ClassLoaderExperiment.localRepository(Paths.get("/root/.m2"), settingsXml);
		assertThat(localRepository).isEqualByComparingTo(Paths.get("/root/.m2/repository"));
	}

	@Test
	public void testLocalRepositoryNotSet() throws Exception {
		Path settingsXml = TestPaths.get("maven-settings-local-repository-not-set.xml");
		Path localRepository = ClassLoaderExperiment.localRepository(Paths.get("/root/.m2"), settingsXml);
		assertThat(localRepository).isEqualByComparingTo(Paths.get("/root/.m2/repository"));
	}

	@Test
	public void testLocalRepositoryPath() throws Exception {
		Path settingsXml = TestPaths.get("maven-settings-local-repository-path.xml");
		Path localRepository = ClassLoaderExperiment.localRepository(Paths.get("/root/.m2"), settingsXml);
		assertThat(localRepository).isEqualByComparingTo(Paths.get("/explicitpath"));
	}

	@Test
	public void testLocalRepositoryEnvironmentVariable() throws Exception {
		Path settingsXml = TestPaths.get("maven-settings-local-repository-environment-variable.xml");
		Path localRepository = ClassLoaderExperiment.localRepository(Paths.get("/root/.m2"), settingsXml);
		assertThat(localRepository)
				.isEqualByComparingTo(Paths.get("/home").resolve(System.getenv("USER")).resolve(".m2/repository"));
	}

	@Test
	public void testLocalRepositorySystemProperty() throws Exception {
		System.setProperty("averyspecificsystempropertykey", "averyspecificsystempropertyvalue");
		Path settingsXml = TestPaths.get("maven-settings-local-repository-system-property.xml");
		Path localRepository = ClassLoaderExperiment.localRepository(Paths.get("/root/.m2"), settingsXml);
		assertThat(localRepository).isEqualByComparingTo(Paths.get("averyspecificsystempropertyvalue/.m2/repository"));
	}

	@Test
	public void testPomWithNoDependenciesAndDependencyManagement() throws Exception {
		Project project = ClassLoaderExperiment.project(TestPaths.get("maven-repository"),
				ClassLoaderExperiment.pomPath("org.slf4j:slf4j-parent:1.7.30"));
		assertThat(project.dependencies).hasSize(1);
		assertThat(project.dependencyManagement.dependencies).hasSize(4);
	}

	@Test
	public void testDependencyWithNoDependencies() throws Exception {
		Collection<String> dependencies = ClassLoaderExperiment.resolveDependencies(TestPaths.get("maven-repository"),
				"org.slf4j:slf4j-api:1.7.30");
		assertThat(dependencies).doesNotHaveDuplicates();
		assertThat(dependencies).containsOnly("org.slf4j:slf4j-api:1.7.30");
	}

	@Test
	public void testDependencyWithOneDependencyManagedByParent() throws Exception {
		Collection<String> dependencies = ClassLoaderExperiment.resolveDependencies(TestPaths.get("maven-repository"),
				"org.slf4j:slf4j-nop:1.7.30");
		assertThat(dependencies).doesNotHaveDuplicates();
		assertThat(dependencies).containsOnly("org.slf4j:slf4j-api:1.7.30", "org.slf4j:slf4j-nop:1.7.30");
	}

	@Test
	public void testOrgHamcrestHamcrestParent11() throws Exception {
		Path localRepository = TestPaths.get("maven-repository");
		Project project = ClassLoaderExperiment.project(localRepository,
				ClassLoaderExperiment.pomPath("org.hamcrest:hamcrest-parent:1.1"));
		Collection<Dependency> dependencies = ClassLoaderExperiment.projectDependencies(localRepository, project, "compile");
		Dependency dependency = new Dependency();
		dependency.groupId = "jmock";
		dependency.artifactId = "jmock";
		dependency.version = "1.1.0";
		dependency.scope = "provided";
		assertThat(dependencies).contains(dependency);
	}

	@Test
	public void testOrgHamcrestHamcrestCore11() throws Exception {
		Path localRepository = TestPaths.get("maven-repository");
		String artifact = "org.hamcrest:hamcrest-core:1.1";
		Project project = ClassLoaderExperiment.project(localRepository, ClassLoaderExperiment.pomPath(artifact));
		Collection<Dependency> dependencies = ClassLoaderExperiment.projectDependencies(localRepository, project, "compile");
		Dependency dependency = new Dependency();
		dependency.groupId = "org.hamcrest";
		dependency.artifactId = "hamcrest-core";
		dependency.version = "1.1";
		assertThat(dependencies).contains(dependency);
	}

	@Test
	public void testJunit410() throws Exception {
		Path localRepository = TestPaths.get("maven-repository");
		Project project = ClassLoaderExperiment.project(localRepository,
				ClassLoaderExperiment.pomPath("junit:junit:4.10"));
		Collection<Dependency> dependencies = ClassLoaderExperiment.projectDependencies(localRepository, project, "compile");
		Dependency dependency = new Dependency();
		dependency.groupId = "org.hamcrest";
		dependency.artifactId = "hamcrest-core";
		dependency.version = "1.1";
		dependency.scope = "compile";
		assertThat(dependencies).contains(dependency);
	}

	@Test
	public void testOrgEclipseJgit561() throws Exception {
		Collection<String> dependencies = ClassLoaderExperiment.resolveDependencies(TestPaths.get("maven-repository"),
				"org.eclipse.jgit:org.eclipse.jgit:5.6.1.202002131546-r");
		assertThat(dependencies).doesNotHaveDuplicates();
		assertThat(dependencies).containsExactly("org.eclipse.jgit:org.eclipse.jgit:5.6.1.202002131546-r",
				"com.jcraft:jsch:0.1.55", "com.jcraft:jzlib:1.1.1", "com.googlecode.javaewah:JavaEWAH:1.1.6",
				"org.slf4j:slf4j-api:1.7.2", "org.bouncycastle:bcpg-jdk15on:1.64",
				"org.bouncycastle:bcpkix-jdk15on:1.64", "org.bouncycastle:bcprov-jdk15on:1.64");
	}

	@Test
	public void testComJcraftJsch0155() throws Exception {
		Collection<String> dependencies = ClassLoaderExperiment.resolveDependencies(TestPaths.get("maven-repository"),
				"com.jcraft:jsch:0.1.55");
		assertThat(dependencies).doesNotHaveDuplicates();
		assertThat(dependencies).containsExactly("com.jcraft:jsch:0.1.55", "com.jcraft:jzlib:1.0.7");
	}
	

	@Test
	public void testDependencyWithJgitDependencies() throws Exception {
		Collection<String> dependencies = ClassLoaderExperiment.resolveDependencies(TestPaths.get("maven-repository"),
				"io.github.arlol:newlinechecker:0.0.1-SNAPSHOT");
		assertThat(dependencies).doesNotHaveDuplicates();
		assertThat(dependencies).contains("org.slf4j:slf4j-api:1.7.30");
		assertThat(dependencies).contains("org.bouncycastle:bcprov-jdk15on:1.64");
	}

	@Test
	public void testDependencyWithTransientDependencies() throws Exception {
		Collection<String> dependencies = ClassLoaderExperiment.resolveDependencies(TestPaths.get("maven-repository"),
				"io.github.arlol:newlinechecker2:0.0.1-SNAPSHOT");
		assertThat(dependencies).doesNotHaveDuplicates();
		assertThat(dependencies).contains("org.slf4j:slf4j-api:1.7.30");
	}

	@Test
	public void testTemplateEnvironmentVariable() throws Exception {
		Entry<String, String> firstEnvironmentVariable = System.getenv().entrySet().iterator().next();
		String template = ClassLoaderExperiment.template("${env." + firstEnvironmentVariable.getKey() + "}");
		assertThat(template).isEqualTo(firstEnvironmentVariable.getValue());
	}

	@Test
	public void testTemplateSystemProperty() throws Exception {
		System.setProperty("averyspecificsystempropertykey", "averyspecificsystempropertyvalue");
		String template = ClassLoaderExperiment.template("${averyspecificsystempropertykey}");
		assertThat(template).isEqualTo("averyspecificsystempropertyvalue");
	}

}
