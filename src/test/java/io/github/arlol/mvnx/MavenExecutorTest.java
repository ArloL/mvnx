package io.github.arlol.mvnx;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;

import io.github.arlol.mvnx.MavenExecutor.Artifact;
import io.github.arlol.mvnx.MavenExecutor.Maven;

public class MavenExecutorTest {

	@Test
	public void testUserHomeM2() {
		Path userHomeM2 = MavenExecutor.Maven.userHomeM2(Paths.get("/root"));
		assertThat(userHomeM2).isEqualByComparingTo(Paths.get("/root/.m2"));
	}

	@Test
	public void testSettingsXml() {
		Path settingsXml = MavenExecutor.Maven.settingsXml(Paths.get("/root/.m2"));
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
		return new MavenExecutor.Maven().localRepository(Paths.get("/root/.m2"), settingsXml);
	}

	@Test
	public void testPomWithNoDependenciesAndDependencyManagement() throws Exception {
		Artifact artifact = artifact("org.slf4j:slf4j-parent:1.7.30");
		assertThat(artifact.dependencies).hasSize(1);
		assertThat(artifact.dependencyManagement).hasSize(4);
	}

	@Test
	public void testDependencyWithNoDependencies() throws Exception {
		Collection<Artifact> dependencies = artifactDependencies("org.slf4j:slf4j-api:1.7.30");
		assertThat(dependencies).containsExactly(d("org.slf4j:slf4j-api:1.7.30"));
	}

	@Test
	public void testDependencyWithOneDependencyManagedByParent() throws Exception {
		Collection<Artifact> dependencies = artifactDependencies("org.slf4j:slf4j-nop:1.7.30");
		assertThat(dependencies).containsExactly(d("org.slf4j:slf4j-nop:1.7.30"), d("org.slf4j:slf4j-api:1.7.30"));
	}

	@Test
	public void testOrgHamcrestHamcrestParent11() throws Exception {
		Collection<Artifact> dependencies = artifactDependencies("org.hamcrest:hamcrest-parent:1.1");
		assertThat(dependencies).isEmpty();
	}

	@Test
	public void testOrgHamcrestHamcrestCore11() throws Exception {
		Collection<Artifact> dependencies = artifactDependencies("org.hamcrest:hamcrest-core:1.1");
		assertThat(dependencies).containsExactly(d("org.hamcrest:hamcrest-core:1.1"));
	}

	@Test
	public void testJunit410() throws Exception {
		Collection<Artifact> dependencies = artifactDependencies("junit:junit:4.10");
		assertThat(dependencies).containsExactly(d("junit:junit:4.10"), d("org.hamcrest:hamcrest-core:1.1", "compile"));
	}

	@Test
	public void testJunit412() throws Exception {
		Collection<Artifact> dependencies = artifactDependencies("junit:junit:4.12");
		assertThat(dependencies).containsExactly(d("junit:junit:4.12"), d("org.hamcrest:hamcrest-core:1.3", "compile"));
	}

	@Test
	public void testOrgEclipseJgit561() throws Exception {
		Collection<Artifact> dependencies = artifactDependencies(
				"org.eclipse.jgit:org.eclipse.jgit:5.6.1.202002131546-r");
		assertThat(dependencies).containsExactly(d("org.eclipse.jgit:org.eclipse.jgit:5.6.1.202002131546-r"),
				d("com.jcraft:jsch:0.1.55"), d("com.jcraft:jzlib:1.1.1"),
				d("com.googlecode.javaewah:JavaEWAH:1.1.6:bundle"), d("org.slf4j:slf4j-api:1.7.2"),
				d("org.bouncycastle:bcpg-jdk15on:1.64"), d("org.bouncycastle:bcprov-jdk15on:1.64"),
				d("org.bouncycastle:bcpkix-jdk15on:1.64"));
	}

	@Test
	public void testComJcraftJsch0155() throws Exception {
		Collection<Artifact> dependencies = artifactDependencies("com.jcraft:jsch:0.1.55");
		assertThat(dependencies).containsExactly(d("com.jcraft:jsch:0.1.55"));
	}

	@Test
	public void testOrgSlf4jNop1730() throws Exception {
		Artifact artifact = artifact("org.slf4j:slf4j-nop:1.7.30");
		assertThat(artifact).isNotNull();
		assertThat(artifact.dependencies).isNotEmpty();
		assertThat(artifact.dependencies).containsExactly(d("org.slf4j:slf4j-api:1.7.30"));
		assertThat(artifact.parent.dependencies).containsExactly(d("junit:junit:4.12", "test"));
	}

	@Test
	public void testUsesSlf4jApi() throws Exception {
		Artifact artifact = artifact("io.github.arlol:uses-slf4j-nop:0.0.1");
		assertThat(artifact).isNotNull();
		assertThat(artifact.dependencies).isNotEmpty();
		assertThat(artifact.dependencies).containsExactly(d("org.slf4j:slf4j-nop:1.7.30"),
				d("org.slf4j:slf4j-api:1.7.23"));
		assertThat(artifact.dependencies.get(0).dependencies).containsExactly(d("org.slf4j:slf4j-api:1.7.23"));
	}

	@Test
	public void testManagesSlf4jApi() throws Exception {
		Artifact artifact = artifact("io.github.arlol:manages-slf4j-api:0.0.1");
		assertThat(artifact).isNotNull();
		assertThat(artifact.dependencies).isNotEmpty();
		assertThat(artifact.dependencies).containsExactly(d("io.github.arlol:uses-slf4j-nop:0.0.1"),
				d("org.slf4j:slf4j-api:1.7.25"));
		assertThat(artifact.dependencies.get(0).dependencies).containsExactly(d("org.slf4j:slf4j-nop:1.7.30"),
				d("org.slf4j:slf4j-api:1.7.25"));
		assertThat(artifact.dependencies.get(0).dependencies.get(0).dependencies)
				.containsExactly(d("org.slf4j:slf4j-api:1.7.25"));
		Collection<Artifact> dependencies = artifactDependencies("io.github.arlol:manages-slf4j-api:0.0.1");

		assertThat(dependencies).containsExactly(d("io.github.arlol:manages-slf4j-api:0.0.1"),
				d("io.github.arlol:uses-slf4j-nop:0.0.1"), d("org.slf4j:slf4j-nop:1.7.30"),
				d("org.slf4j:slf4j-api:1.7.25"));
	}

	@Test
	public void testDependencyWithJgitDependencies() throws Exception {
		Collection<Artifact> dependencies = artifactDependencies("io.github.arlol:newlinechecker:0.0.1-SNAPSHOT");
		assertThat(dependencies).containsExactly(d("io.github.arlol:newlinechecker:0.0.1-SNAPSHOT"),
				d("org.eclipse.jgit:org.eclipse.jgit:5.6.1.202002131546-r"), d("com.jcraft:jsch:0.1.55"),
				d("com.jcraft:jzlib:1.1.1"), d("com.googlecode.javaewah:JavaEWAH:1.1.6:bundle"),
				d("org.slf4j:slf4j-api:1.7.30"), d("org.bouncycastle:bcpg-jdk15on:1.64"),
				d("org.bouncycastle:bcprov-jdk15on:1.64"), d("org.bouncycastle:bcpkix-jdk15on:1.64"),
				d("org.slf4j:slf4j-nop:1.7.30"));
	}

	@Test
	public void testDependencyWithTransientDependencies() throws Exception {
		Collection<Artifact> dependencies = artifactDependencies("io.github.arlol:newlinechecker2:0.0.1-SNAPSHOT");
		assertThat(dependencies).containsExactly(d("io.github.arlol:newlinechecker2:0.0.1-SNAPSHOT"),
				d("org.slf4j:slf4j-nop:1.7.30"), d("org.slf4j:slf4j-api:1.7.30"));
	}

	@Test
	public void testPrint() throws Exception {
		Collection<Artifact> dependencies = artifactDependencies("io.github.arlol:print:0.0.1");
		assertThat(dependencies).containsExactly(d("io.github.arlol:print:0.0.1"));
	}

	@Test
	public void testTemplateEnvironmentVariable() throws Exception {
		Entry<String, String> firstEnvironmentVariable = System.getenv().entrySet().iterator().next();
		String template = MavenExecutor.Maven.template("${env." + firstEnvironmentVariable.getKey() + "}",
				Collections.emptyMap());
		assertThat(template).isEqualTo(firstEnvironmentVariable.getValue());
	}

	@Test
	public void testTemplateSystemProperty() throws Exception {
		System.setProperty("averyspecificsystempropertykey", "averyspecificsystempropertyvalue");
		String template = MavenExecutor.Maven.template("${averyspecificsystempropertykey}", Collections.emptyMap());
		assertThat(template).isEqualTo("averyspecificsystempropertyvalue");
	}

	@Test
	public void testPropertyReferencingOtherProperty() throws Exception {
		Map<String, String> properties = Map.of("key1", "${key2}", "key2", "value");
		String template = MavenExecutor.Maven.template("${key1}", properties);
		assertThat(template).isEqualTo("value");
	}

	@Test
	public void testUnresolvableProperty() throws Exception {
		String template = MavenExecutor.Maven.template("${key1}", Collections.emptyMap());
		assertThat(template).isEqualTo("${key1}");
	}

	/**
	 * groupId:artifactId:version[:packaging][:classifier]
	 */
	public Artifact d(String artifact) {
		Artifact dependency = new Artifact();
		String[] parts = artifact.split(":");
		dependency.groupId = parts[0];
		dependency.artifactId = parts[1];
		dependency.version = parts[2];
		if (parts.length > 3) {
			dependency.packaging = parts[3];
		} else {
			dependency.packaging = "jar";
		}
		if (parts.length > 4) {
			dependency.classifier = parts[4];
		}
		dependency.scope = "compile";
		return dependency;
	}

	public Artifact d(String artifact, String scope) {
		Artifact dependency = d(artifact);
		dependency.scope = scope;
		return dependency;
	}

	public Artifact artifact(String artifactIdentifier) throws Exception {
		Maven maven = new MavenExecutor.Maven();
		maven.inMemory = true;
		maven.localRepository = TestPaths.get("maven-repository");
		maven.repositories = Collections.singleton("http://localhost:62085");
		Artifact artifact = d(artifactIdentifier);
		maven.resolve(artifact, Collections.emptyList());
		return artifact;
	}

	public Collection<Artifact> artifactDependencies(String artifactIdentifier) throws Exception {
		Artifact artifact = artifact(artifactIdentifier);
		Collection<Artifact> dependencies = artifact.dependencies(dependency -> !(dependency.packaging.equals("pom")
				|| "test".equals(dependency.scope) || "provided".equals(dependency.scope) || dependency.optional));
		assertThat(dependencies).doesNotHaveDuplicates();
		return dependencies;
	}

}
