package io.github.arlol.minimaven;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Project {

	Dependency parent;
	String groupId;
	String artifactId;
	String version;
	DependencyManagement dependencyManagement;
	List<Dependency> dependencies = new ArrayList<>();
	Properties properties = new Properties();

	@Override
	public String toString() {
		return "Project [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + "]";
	}

}
