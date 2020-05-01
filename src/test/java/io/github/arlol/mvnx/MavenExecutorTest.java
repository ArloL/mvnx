package io.github.arlol.mvnx;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map.Entry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MavenExecutorTest {

	private Path localRepository;

	@BeforeEach
	public void setup() throws Exception {
		localRepository = TestPaths.get("maven-repository");
	}

	@Test
	public void testUserHome() {
		Path userHome = MavenExecutor.userHome();
		assertThat(userHome).exists();
	}

	@Test
	public void testUserHomeM2() {
		Path userHomeM2 = MavenExecutor.userHomeM2(Paths.get("/root"));
		assertThat(userHomeM2).isEqualByComparingTo(Paths.get("/root/.m2"));
	}

	@Test
	public void testSettingsXml() {
		Path settingsXml = MavenExecutor.settingsXml(Paths.get("/root/.m2"));
		assertThat(settingsXml).isEqualByComparingTo(Paths.get("/root/.m2/settings.xml"));
	}

	@Test
	public void testLocalRepositoryNoSettingsXml() throws Exception {
		Path settingsXml = Paths.get("THIS-FILE-DOES-NOT-EXIST.xml");
		Path localRepository = localRepository(settingsXml);
		assertThat(localRepository).isEqualByComparingTo(Paths.get("/root/.m2/repository"));
	}

	@Test
	public void testLocalRepositoryEmpty() throws Exception {
		Path settingsXml = TestPaths.get("maven-settings-local-repository-empty.xml");
		Path localRepository = localRepository(settingsXml);
		assertThat(localRepository).isEqualByComparingTo(Paths.get("/root/.m2/repository"));
	}

	@Test
	public void testLocalRepositoryNotSet() throws Exception {
		Path settingsXml = TestPaths.get("maven-settings-local-repository-not-set.xml");
		Path localRepository = localRepository(settingsXml);
		assertThat(localRepository).isEqualByComparingTo(Paths.get("/root/.m2/repository"));
	}

	@Test
	public void testLocalRepositoryPath() throws Exception {
		Path settingsXml = TestPaths.get("maven-settings-local-repository-path.xml");
		Path localRepository = localRepository(settingsXml);
		assertThat(localRepository).isEqualByComparingTo(Paths.get("/explicitpath"));
	}

	@Test
	public void testLocalRepositoryEnvironmentVariable() throws Exception {
		String getenv = System.getenv("USER");
		assertThat(getenv).isNotNull();
		Path settingsXml = TestPaths.get("maven-settings-local-repository-environment-variable.xml");
		Path localRepository = localRepository(settingsXml);
		assertThat(localRepository).isEqualByComparingTo(Paths.get("/home").resolve(getenv).resolve(".m2/repository"));
	}

	@Test
	public void testLocalRepositorySystemProperty() throws Exception {
		System.setProperty("averyspecificsystempropertykey", "averyspecificsystempropertyvalue");
		Path settingsXml = TestPaths.get("maven-settings-local-repository-system-property.xml");
		Path localRepository = localRepository(settingsXml);
		assertThat(localRepository).isEqualByComparingTo(Paths.get("averyspecificsystempropertyvalue/.m2/repository"));
	}

	private Path localRepository(Path settingsXml) {
		return new MavenExecutor().localRepository(Paths.get("/root/.m2"), settingsXml);
	}

	@Test
	public void testPomWithNoDependenciesAndDependencyManagement() throws Exception {
		Project project = project("org.slf4j:slf4j-parent:1.7.30");
		assertThat(project.dependencies).hasSize(1);
		assertThat(project.dependencyManagement.dependencies).hasSize(4);
	}

	@Test
	public void testDependencyWithNoDependencies() throws Exception {
		Collection<Dependency> dependencies = projectDependencies("org.slf4j:slf4j-api:1.7.30");
		assertThat(dependencies).containsExactly(d("org.slf4j:slf4j-api:1.7.30"));
	}

	@Test
	public void testDependencyWithOneDependencyManagedByParent() throws Exception {
		Collection<Dependency> dependencies = projectDependencies("org.slf4j:slf4j-nop:1.7.30");
		assertThat(dependencies).containsExactly(d("org.slf4j:slf4j-nop:1.7.30"), d("org.slf4j:slf4j-api:1.7.30"));
	}

	@Test
	public void testOrgHamcrestHamcrestParent11() throws Exception {
		Collection<Dependency> dependencies = projectDependencies("org.hamcrest:hamcrest-parent:1.1");
		assertThat(dependencies).containsExactly(d("org.hamcrest:hamcrest-parent:1.1"));
	}

	@Test
	public void testOrgHamcrestHamcrestCore11() throws Exception {
		Collection<Dependency> dependencies = projectDependencies("org.hamcrest:hamcrest-core:1.1");
		assertThat(dependencies).containsExactly(d("org.hamcrest:hamcrest-core:1.1"));
	}

	@Test
	public void testJunit410() throws Exception {
		Collection<Dependency> dependencies = projectDependencies("junit:junit:4.10");
		assertThat(dependencies).containsExactly(d("junit:junit:4.10"), d("org.hamcrest:hamcrest-core:1.1", "compile"));
	}

	@Test
	public void testJunit412() throws Exception {
		Collection<Dependency> dependencies = projectDependencies("junit:junit:4.12");
		assertThat(dependencies).containsExactly(d("junit:junit:4.12"), d("org.hamcrest:hamcrest-core:1.3", "compile"));
	}

	@Test
	public void testOrgEclipseJgit561() throws Exception {
		Collection<Dependency> dependencies = projectDependencies(
				"org.eclipse.jgit:org.eclipse.jgit:5.6.1.202002131546-r");
		assertThat(dependencies).containsExactly(d("org.eclipse.jgit:org.eclipse.jgit:5.6.1.202002131546-r"),
				d("com.jcraft:jsch:0.1.55"), d("com.jcraft:jzlib:1.1.1"), d("com.googlecode.javaewah:JavaEWAH:1.1.6"),
				d("org.slf4j:slf4j-api:1.7.2"), d("org.bouncycastle:bcpg-jdk15on:1.64"),
				d("org.bouncycastle:bcprov-jdk15on:1.64"), d("org.bouncycastle:bcpkix-jdk15on:1.64"));
	}

	@Test
	public void testComJcraftJsch0155() throws Exception {
		Collection<Dependency> dependencies = projectDependencies("com.jcraft:jsch:0.1.55");
		assertThat(dependencies).containsExactly(d("com.jcraft:jsch:0.1.55"));
	}

	@Test
	public void testOrgSlf4jNop1730() throws Exception {
		Project project = project("org.slf4j:slf4j-nop:1.7.30");
		assertThat(project).isNotNull();
		assertThat(project.dependencies).isNotEmpty();
		assertThat(project.dependencies).containsExactly(d("org.slf4j:slf4j-api:1.7.30"));
		assertThat(project.parent.project.dependencies).containsExactly(d("junit:junit:4.12", "test"));
	}

	@Test
	public void testUsesSlf4jApi() throws Exception {
		Project project = project("io.github.arlol:uses-slf4j-nop:0.0.1");
		assertThat(project).isNotNull();
		assertThat(project.dependencies).isNotEmpty();
		assertThat(project.dependencies).containsExactly(d("org.slf4j:slf4j-nop:1.7.30"),
				d("org.slf4j:slf4j-api:1.7.23"));
		assertThat(project.dependencies.get(0).project.dependencies).containsExactly(d("org.slf4j:slf4j-api:1.7.23"));
	}

	@Test
	public void testManagesSlf4jApi() throws Exception {
		Project project = project("io.github.arlol:manages-slf4j-api:0.0.1");
		assertThat(project).isNotNull();
		assertThat(project.dependencies).isNotEmpty();
		assertThat(project.dependencies).containsExactly(d("io.github.arlol:uses-slf4j-nop:0.0.1"),
				d("org.slf4j:slf4j-api:1.7.25"));
		assertThat(project.dependencies.get(0).project.dependencies).containsExactly(d("org.slf4j:slf4j-nop:1.7.30"),
				d("org.slf4j:slf4j-api:1.7.25"));
		assertThat(project.dependencies.get(0).project.dependencies.get(0).project.dependencies)
				.containsExactly(d("org.slf4j:slf4j-api:1.7.25"));
		Collection<Dependency> dependencies = projectDependencies("io.github.arlol:manages-slf4j-api:0.0.1");

		assertThat(dependencies).containsExactly(d("io.github.arlol:manages-slf4j-api:0.0.1"),
				d("io.github.arlol:uses-slf4j-nop:0.0.1"), d("org.slf4j:slf4j-nop:1.7.30"),
				d("org.slf4j:slf4j-api:1.7.25"));
	}

	@Test
	public void testDependencyWithJgitDependencies() throws Exception {
		Collection<Dependency> dependencies = projectDependencies("io.github.arlol:newlinechecker:0.0.1-SNAPSHOT");
		assertThat(dependencies).containsExactly(d("io.github.arlol:newlinechecker:0.0.1-SNAPSHOT"),
				d("org.eclipse.jgit:org.eclipse.jgit:5.6.1.202002131546-r"), d("com.jcraft:jsch:0.1.55"),
				d("com.jcraft:jzlib:1.1.1"), d("com.googlecode.javaewah:JavaEWAH:1.1.6"),
				d("org.slf4j:slf4j-api:1.7.30"), d("org.bouncycastle:bcpg-jdk15on:1.64"),
				d("org.bouncycastle:bcprov-jdk15on:1.64"), d("org.bouncycastle:bcpkix-jdk15on:1.64"),
				d("org.slf4j:slf4j-nop:1.7.30"));
	}

	@Test
	public void testDependencyWithTransientDependencies() throws Exception {
		Collection<Dependency> dependencies = projectDependencies("io.github.arlol:newlinechecker2:0.0.1-SNAPSHOT");
		assertThat(dependencies).containsExactly(d("io.github.arlol:newlinechecker:0.0.1-SNAPSHOT"),
				d("org.slf4j:slf4j-nop:1.7.30"), d("org.slf4j:slf4j-api:1.7.30"));
	}

	@Test
	public void testTemplateEnvironmentVariable() throws Exception {
		Entry<String, String> firstEnvironmentVariable = System.getenv().entrySet().iterator().next();
		String template = MavenExecutor.template("${env." + firstEnvironmentVariable.getKey() + "}",
				Collections.emptyMap());
		assertThat(template).isEqualTo(firstEnvironmentVariable.getValue());
	}

	@Test
	public void testTemplateSystemProperty() throws Exception {
		System.setProperty("averyspecificsystempropertykey", "averyspecificsystempropertyvalue");
		String template = MavenExecutor.template("${averyspecificsystempropertykey}", Collections.emptyMap());
		assertThat(template).isEqualTo("averyspecificsystempropertyvalue");
	}

	public Dependency d(String artifact) {
		Dependency dependency = new Dependency();
		String[] parts = artifact.split(":");
		dependency.groupId = parts[0];
		dependency.artifactId = parts[1];
		dependency.version = parts[2];
		return dependency;
	}

	public Dependency d(String artifact, String scope) {
		Dependency dependency = d(artifact);
		dependency.scope = scope;
		return dependency;
	}

	public Project project(String artifact) throws Exception {
		MavenExecutor mavenExecutor = new MavenExecutor();
		mavenExecutor.localRepository = localRepository;
		mavenExecutor.repositories = Collections.singleton("https://repo1.maven.org/maven2/");
		return mavenExecutor.project(MavenExecutor.pomPath(d(artifact)), Collections.emptyList());
	}

	public Collection<Dependency> projectDependencies(String artifact) throws Exception {
		Project project = project(artifact);
		Collection<Dependency> dependencies = MavenExecutor.projectDependencies(project, project);
		assertThat(dependencies).doesNotHaveDuplicates();
		return dependencies;
	}

}
