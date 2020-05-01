package io.github.arlol.mvnx;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Predicate;

public class Dependency {

	String groupId;
	String artifactId;
	String version;
	String type;
	String classifier;
	String scope;
	boolean optional = false;
	Project project;

	public Collection<Dependency> dependencies(Predicate<Dependency> filter) {
		Collection<Dependency> dependencies = new LinkedHashSet<>();
		if (filter.test(this)) {
			dependencies.add(this);
		}
		if (project.parent != null) {
			dependencies.addAll(project.parent.dependencies(filter));
		}
		for (Dependency dependency : project.dependencies) {
			if (filter.test(dependency)) {
				dependencies.addAll(dependency.dependencies(filter));
			}
		}
		return dependencies;

	}

	public boolean equalsArtifact(Dependency other) {
		return Objects.equals(artifactId, other.artifactId) && Objects.equals(classifier, other.classifier)
				&& Objects.equals(groupId, other.groupId) && Objects.equals(type, other.type);
	}

	@Override
	public int hashCode() {
		return Objects.hash(artifactId, classifier, groupId, type, version);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Dependency other = (Dependency) obj;
		return Objects.equals(artifactId, other.artifactId) && Objects.equals(classifier, other.classifier)
				&& Objects.equals(groupId, other.groupId) && Objects.equals(type, other.type)
				&& Objects.equals(version, other.version);
	}

	@Override
	public String toString() {
		return "Dependency [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + ", packaging="
				+ type + ", classifier=" + classifier + ", scope=" + scope + "]";
	}

}
