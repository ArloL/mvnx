package io.github.arlol.mvnx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Project {

	Dependency parent;
	String groupId;
	String artifactId;
	String version;
	String packaging;
	DependencyManagement dependencyManagement;
	List<Dependency> dependencies = new ArrayList<>();
	Map<String, String> properties = new HashMap<>();
	String remote;

	@Override
	public String toString() {
		return "Project [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + "]";
	}

}
