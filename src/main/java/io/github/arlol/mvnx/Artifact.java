package io.github.arlol.mvnx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Artifact {

	Artifact parent;
	String groupId;
	String artifactId;
	String version;
	String packaging;
	String classifier;
	String scope;
	boolean optional = false;
	List<Artifact> dependencyManagement = new ArrayList<>();
	List<Artifact> dependencies = new ArrayList<>();
	Map<String, String> properties = new HashMap<>();
	String remote;

	public Collection<Artifact> dependencies(Predicate<Artifact> filter) {
		Collection<Artifact> dependencies = new LinkedHashSet<>();
		if (filter.test(this)) {
			dependencies.add(this);
		}
		if (parent != null) {
			dependencies.addAll(parent.dependencies(filter));
		}
		for (Artifact dependency : this.dependencies) {
			if (filter.test(dependency)) {
				dependencies.addAll(dependency.dependencies(filter));
			}
		}
		return dependencies;
	}

	public List<Artifact> hierarchy() {
		List<Artifact> hierarchy = new ArrayList<>();
		if (parent != null) {
			hierarchy.addAll(parent.hierarchy());
		}
		hierarchy.add(this);
		return hierarchy;
	}

	public Map<String, String> allProperties(List<Artifact> dependents) {
		Map<String, String> map = new HashMap<>();
		if (parent != null) {
			map.putAll(parent.allProperties(List.of()));
		}
		map.putAll(properties);
		for (Artifact dependent : dependents) {
			map.putAll(dependent.allProperties(List.of()));
		}
		return map;
	}

	public void manage(List<Artifact> dependents) {
		Artifact override = new Artifact();
		List<Artifact> artifacts = dependents.stream().flatMap(artifact -> artifact.hierarchy().stream()).flatMap(
				artifact -> Stream.concat(artifact.dependencies.stream(), artifact.dependencyManagement.stream()))
				.filter(this::equalsArtifact).collect(Collectors.toList());
		for (Artifact artifact : artifacts) {
			if (override.version == null) {
				override.version = artifact.version;
			}
			if (override.scope == null) {
				override.scope = artifact.scope;
			}
			if (override.version != null && override.scope != null) {
				break;
			}
		}
		if (override.version != null) {
			this.version = override.version;
		}
		if (override.scope == null) {
			override.scope = "compile";
		}
		this.scope = override.scope;
	}

	public boolean equalsArtifact(Artifact other) {
		return Objects.equals(artifactId, other.artifactId) && Objects.equals(classifier, other.classifier)
				&& Objects.equals(groupId, other.groupId) && Objects.equals(packaging, other.packaging);
	}

	@Override
	public int hashCode() {
		return Objects.hash(artifactId, classifier, groupId, packaging, version);
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
		Artifact other = (Artifact) obj;
		return Objects.equals(artifactId, other.artifactId) && Objects.equals(classifier, other.classifier)
				&& Objects.equals(groupId, other.groupId) && Objects.equals(packaging, other.packaging)
				&& Objects.equals(version, other.version);
	}

	@Override
	public String toString() {
		return "Dependency [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + ", packaging="
				+ packaging + ", classifier=" + classifier + ", scope=" + scope + "]";
	}

}
