package io.github.arlol.minimaven;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ClassLoaderExperiment {

	private static final Pattern ENVIRONMENT_VARIABLES_TOKEN = Pattern.compile("\\$\\{env.([\\w_]+)\\}");
	private static final Pattern PROPERTIES_TOKEN = Pattern.compile("\\$\\{([\\w.-]+)\\}");
	private static final long TIMEOUT_MS = 10_000;

	public static void main(String[] args) throws Exception {
		String mainClass = "io.github.arlol.newlinechecker.NewlinecheckerApplication";
		Path userHomeM2 = userHomeM2(userHome());
		Path localRepository = localRepository(userHomeM2, settingsXml(userHomeM2));
		String artifact = "io.github.arlol:newlinechecker:0.0.1-SNAPSHOT";
		Project project = project(localRepository, pomPath(artifact));
		Collection<Dependency> dependencies = projectDependencies(localRepository, project, project, "compile");
		URL[] jars = dependencies.stream().map(ClassLoaderExperiment::jarPath).map(localRepository::resolve)
				.map(Path::toUri).map(ClassLoaderExperiment::toURL).toArray(URL[]::new);
		URLClassLoader classLoader = new URLClassLoader(jars, ClassLoaderExperiment.class.getClassLoader());
		Class<?> classToLoad = Class.forName(mainClass, true, classLoader);
		classToLoad.getMethod("main", new Class[] { args.getClass() }).invoke(null, new Object[] { args });
	}

	public static Collection<String> resolveDependencies(Path localRepository, String artifact) throws Exception {
		Project project = project(localRepository, pomPath(artifact));
		Collection<Dependency> dependencies = projectDependencies(localRepository, project, project, "compile");
		return dependencies.stream().filter(d -> d.scope == null || "compile".equals(d.scope))
				.map(d -> d.groupId + ":" + d.artifactId + ":" + d.version)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	public static Collection<Dependency> projectDependencies(Path localRepository, Project project, String scope)
			throws Exception {
		return projectDependencies(localRepository, project, project, scope);
	}

	public static Collection<Dependency> projectDependencies(Path localRepository, Project rootProject, Project project,
			String scope) throws Exception {
		Collection<Dependency> dependencies = new LinkedHashSet<>();
		if (rootProject == project) {
			Dependency dependency = new Dependency();
			dependency.groupId = project.groupId;
			dependency.artifactId = project.artifactId;
			dependency.version = project.version;
			dependencies.add(dependency);
		}
		if (project.parent != null) {
			projectDependencies(localRepository, rootProject, project.parent, scope).stream()
					.filter(dependency -> dependency.scope == null
							|| (!"provided".equals(dependency.scope)) && (!"test".equals(dependency.scope)))
					.forEach(dependency -> {
						manageDependency(project, dependency);
						if (!dependencies.add(dependency)) {
							dependencies.remove(dependency);
							dependencies.add(dependency);
						}
					});
		}
		for (Dependency projectDependency : project.dependencies) {
			Project dependencyProject = project(localRepository, pomPath(projectDependency));
			Collection<Dependency> dependencyDependencies = projectDependencies(localRepository, rootProject,
					dependencyProject, scope);
			manageDependency(rootProject, projectDependency);
			manageDependency(project, projectDependency);
			manageDependency(dependencyProject, projectDependency);
			if (!dependencies.add(projectDependency)) {
				dependencies.remove(projectDependency);
				dependencies.add(projectDependency);
			}
			if ("test".equals(projectDependency.scope)) {
				dependencyDependencies.forEach(d -> {
					if (d.scope == null || "compile".equals(d.scope)) {
						d.scope = "test";
					}
				});
			}
			if ("provided".equals(projectDependency.scope)) {
				dependencyDependencies.forEach(d -> {
					if (d.scope == null || "compile".equals(d.scope)) {
						d.scope = "provided";
					}
				});
			}
			dependencyDependencies.stream()
					.filter(dependency -> dependency.scope == null
							|| (!"provided".equals(dependency.scope)) && (!"test".equals(dependency.scope)))
					.forEach(dependency -> {
						manageDependency(rootProject, dependency);
						manageDependency(project, dependency);
						manageDependency(dependencyProject, dependency);
						if (!dependencies.add(dependency)) {
							dependencies.remove(dependency);
							dependencies.add(dependency);
						}
					});
		}
		return dependencies;
	}

	private static void manageDependency(Project project, Dependency dependency) {
		Project searchProject = project;
		while (searchProject != null) {
			if (searchProject.dependencyManagement != null) {
				searchProject.dependencyManagement.dependencies.stream()
						.filter(d -> d.groupId.equals(dependency.groupId) && d.artifactId.equals(dependency.artifactId))
						.findFirst().ifPresent(md -> {
							if (dependency.version == null) {
								dependency.version = md.version;
							}
							if (dependency.scope == null) {
								dependency.scope = md.scope;
							}
						});
			}
			searchProject = searchProject.parent;
		}
	}

	public static Path jarPath(Dependency dependency) {
		return jarPath(dependency.groupId, dependency.artifactId, dependency.version);
	}

	public static Path jarPath(String groupId, String artifactId, String version) {
		return Paths.get(groupId.replace(".", "/")).resolve(artifactId).resolve(version)
				.resolve(artifactId + "-" + version + ".jar");
	}

	public static Path pomPath(Dependency dependency) {
		return pomPath(dependency.groupId, dependency.artifactId, dependency.version);
	}

	public static Path pomPath(String artifact) {
		String[] parts = artifact.split(":");
		String groupId = parts[0];
		String artifactId = parts[1];
		String version = parts[2];
		return pomPath(groupId, artifactId, version);
	}

	public static Path pomPath(String groupId, String artifactId, String version) {
		return Paths.get(groupId.replace(".", "/")).resolve(artifactId).resolve(version)
				.resolve(artifactId + "-" + version + ".pom");
	}

	public static Path userHome() {
		return Paths.get(System.getProperty("user.home"));
	}

	public static Path userHomeM2(Path userHome) {
		return userHome.resolve(".m2");
	}

	public static Path settingsXml(Path userHomeM2) {
		return userHomeM2.resolve("settings.xml");
	}

	public static Path localRepository(Path userHomeM2, Path settingsXml) throws Exception {
		if (Files.exists(settingsXml)) {
			NodeList nList = xmlDocument(settingsXml).getElementsByTagName("localRepository");
			if (nList.getLength() == 1) {
				String localRepository = nList.item(0).getTextContent();
				if (!localRepository.isBlank()) {
					localRepository = template(localRepository);
					return Paths.get(localRepository);
				}
			}
		}
		return userHomeM2.resolve("repository");
	}

	public static String template(String text) {
		return templateSystemProperties(templateEnvironmentVariables(text));
	}

	public static String template(String text, Properties properties) {
		final Matcher mat = PROPERTIES_TOKEN.matcher(text);
		StringBuilder result = new StringBuilder();
		int last = 0;
		while (mat.find()) {
			result.append(text.substring(last, mat.start()));
			final String key = mat.group(1);
			Object value = properties.get(key);
			if (value != null) {
				result.append(value);
			} else {
				result.append("${");
				result.append(key);
				result.append("}");
			}
			last = mat.end();
		}
		result.append(text.substring(last));
		return result.toString();
	}

	public static String templateSystemProperties(String text) {
		return template(text, System.getProperties());
	}

	public static String templateEnvironmentVariables(String text) {
		final Matcher mat = ENVIRONMENT_VARIABLES_TOKEN.matcher(text);
		StringBuilder result = new StringBuilder();
		int last = 0;
		while (mat.find()) {
			result.append(text.substring(last, mat.start()));
			final String key = mat.group(1);
			String value = System.getenv(key);
			if (value != null && !value.isBlank()) {
				result.append(value);
			} else {
				result.append("${env.");
				result.append(key);
				result.append("}");
			}
			last = mat.end();
		}
		result.append(text.substring(last));
		return result.toString();
	}

	public static Document xmlDocument(Path xml) throws Exception {
		DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document document = documentBuilder.parse(xml.toFile());
		document.getDocumentElement().normalize();
		return document;
	}

	public static String getTextContentFromFirstElementByTagName(Element element, String tagName) {
		Node node = element.getElementsByTagName(tagName).item(0);
		return Optional.ofNullable(node).map(Node::getTextContent).orElse(null);
	}

	public static List<Element> getChildElementsByTagName(Element element, String tagName) {
		List<Element> result = new ArrayList<>();
		NodeList childNodes = element.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node item = childNodes.item(i);
			if (tagName.equals(item.getNodeName())) {
				result.add((Element) item);
			}
		}
		return result;
	}

	public static Project project(Path localRepository, Path pom) throws Exception {
		Project project = new Project();

		Path absolutePomPath = localRepository.resolve(pom);
		if (!Files.exists(absolutePomPath)) {
			Files.createDirectories(absolutePomPath.getParent());
			URI pomUri = URI.create("https://repo1.maven.org/maven2/").resolve(pom.toString());
			HttpRequest request = HttpRequest.newBuilder().version(HttpClient.Version.HTTP_1_1).uri(pomUri)
					.timeout(Duration.ofMillis(TIMEOUT_MS)).build();
			HttpResponse<Path> response = HttpClient.newBuilder().build().send(request,
					HttpResponse.BodyHandlers.ofFile(Files.createTempFile(null, null)));
			if (response.statusCode() != 200) {
				throw new IllegalArgumentException("Download failed");
			}
			if (!Files.exists(response.body())) {
				throw new IllegalArgumentException("Download failed");
			}
			Files.move(response.body(), absolutePomPath);
		}

		Document artifactPomXml = xmlDocument(absolutePomPath);
		Element projectElement = artifactPomXml.getDocumentElement();

		List<Element> parent = getChildElementsByTagName(projectElement, "parent");
		if (parent.size() == 1) {
			Element element = parent.get(0);
			String groupId = getTextContentFromFirstElementByTagName(element, "groupId");
			String artifactId = getTextContentFromFirstElementByTagName(element, "artifactId");
			String version = getTextContentFromFirstElementByTagName(element, "version");
			project.parent = project(localRepository, pomPath(groupId, artifactId, version));
		}

		project.artifactId = getChildElementsByTagName(projectElement, "artifactId").get(0).getTextContent();
		List<Element> groupIdElements = getChildElementsByTagName(projectElement, "groupId");
		if (!groupIdElements.isEmpty()) {
			project.groupId = groupIdElements.get(0).getTextContent();
		} else {
			project.groupId = project.parent.groupId;
		}
		List<Element> versionElements = getChildElementsByTagName(projectElement, "version");
		if (!versionElements.isEmpty()) {
			project.version = versionElements.get(0).getTextContent();
		} else {
			project.version = project.parent.version;
		}

		project.properties.put("project.version", project.version);

		List<Element> propertiesElements = getChildElementsByTagName(projectElement, "properties");
		if (!propertiesElements.isEmpty()) {
			NodeList properties = propertiesElements.get(0).getChildNodes();
			for (int i = 0; i < properties.getLength(); i++) {
				Node node = properties.item(i);
				if (node.getNodeType() == Node.ELEMENT_NODE) {
					Element element = (Element) node;
					project.properties.put(element.getTagName(), element.getTextContent());
				}
			}
		}

		List<Element> dependencyManagementElements = getChildElementsByTagName(projectElement, "dependencyManagement");
		if (!dependencyManagementElements.isEmpty()) {
			NodeList dependencies = dependencyManagementElements.get(0).getElementsByTagName("dependency");
			project.dependencyManagement = new DependencyManagement();
			for (int i = 0; i < dependencies.getLength(); i++) {
				Element dependency = (Element) dependencies.item(i);
				Dependency dep = new Dependency();
				dep.groupId = getTextContentFromFirstElementByTagName(dependency, "groupId");
				dep.artifactId = getTextContentFromFirstElementByTagName(dependency, "artifactId");
				dep.version = getTextContentFromFirstElementByTagName(dependency, "version");
				dep.scope = getTextContentFromFirstElementByTagName(dependency, "scope");
				if (dep.version != null) {
					dep.version = template(dep.version, project.properties);
				}
				project.dependencyManagement.dependencies.add(dep);
			}
		}

		List<Element> dependenciesElements = getChildElementsByTagName(projectElement, "dependencies");
		if (!dependenciesElements.isEmpty()) {
			NodeList dependencies = dependenciesElements.get(0).getElementsByTagName("dependency");
			for (int i = 0; i < dependencies.getLength(); i++) {
				Element dependency = (Element) dependencies.item(i);
				Dependency dep = new Dependency();
				dep.groupId = getTextContentFromFirstElementByTagName(dependency, "groupId");
				dep.artifactId = getTextContentFromFirstElementByTagName(dependency, "artifactId");
				dep.version = getTextContentFromFirstElementByTagName(dependency, "version");
				if (dep.version != null) {
					dep.version = template(dep.version, project.properties);
				} else {
					manageDependency(project, dep);
				}
				dep.scope = getTextContentFromFirstElementByTagName(dependency, "scope");
				project.dependencies.add(dep);
			}
		}

		return project;
	}

	public static URL toURL(URI uri) {
		try {
			return uri.toURL();
		} catch (MalformedURLException e) {
			throw new UncheckedIOException(e);
		}
	}

}
