package io.github.arlol.minimaven;

import java.util.Objects;

public class Dependency {

	String groupId;
	String artifactId;
	String version;
	String packaging;
	String classifier;
	String scope;

	@Override
	public int hashCode() {
		return Objects.hash(artifactId, classifier, groupId, packaging, scope);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Dependency other = (Dependency) obj;
		return Objects.equals(artifactId, other.artifactId) && Objects.equals(classifier, other.classifier)
				&& Objects.equals(groupId, other.groupId) && Objects.equals(packaging, other.packaging)
				&& Objects.equals(scope, other.scope);
	}

	@Override
	public String toString() {
		return "Dependency [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + ", packaging="
				+ packaging + ", classifier=" + classifier + ", scope=" + scope + "]";
	}

}