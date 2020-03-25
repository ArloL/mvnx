package io.github.arlol.minimaven;

public class DependencyBuilder {

	private String groupId;
	private String artifactId;
	private String version;
	private String packaging;
	private String classifier;
	private String scope;

	public static DependencyBuilder builder() {
		return new DependencyBuilder(new Dependency());
	}

	private DependencyBuilder(Dependency dependency) {
		this.groupId = dependency.groupId;
		this.artifactId = dependency.artifactId;
		this.version = dependency.version;
		this.packaging = dependency.packaging;
		this.classifier = dependency.classifier;
		this.scope = dependency.scope;
	}

	public DependencyBuilder groupId(String groupId) {
		this.groupId = groupId;
		return this;
	}

	public DependencyBuilder artifactId(String artifactId) {
		this.artifactId = artifactId;
		return this;
	}

	public DependencyBuilder version(String version) {
		this.version = version;
		return this;
	}

	public DependencyBuilder packaging(String packaging) {
		this.packaging = packaging;
		return this;
	}

	public DependencyBuilder classifier(String classifier) {
		this.classifier = classifier;
		return this;
	}

	public DependencyBuilder scope(String scope) {
		this.scope = scope;
		return this;
	}

	public Dependency build() {
		Dependency dependency = new Dependency();
		dependency.groupId = this.groupId;
		dependency.artifactId = this.artifactId;
		dependency.version = this.version;
		dependency.packaging = this.packaging;
		dependency.classifier = this.classifier;
		dependency.scope = this.scope;
		return dependency;
	}

}
