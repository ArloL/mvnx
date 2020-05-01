package io.github.arlol.mvnx;

import java.io.ByteArrayInputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MavenExecutor {

	private static final Pattern PROPERTIES_TOKEN = Pattern.compile("\\$\\{([\\w.-]+)\\}");
	private static final long TIMEOUT_MS = 10_000;

	public static void main(String[] args) throws Exception {
		String mainClass = "io.github.arlol.newlinechecker.NewlinecheckerApplication";
		Path userHomeM2 = userHomeM2(userHome());
		Path localRepository = localRepository(userHomeM2, settingsXml(userHomeM2));
		List<String> remotes = List.of("https://repo1.maven.org/maven2/", "https://jitpack.io/");
		System.out.println(System.currentTimeMillis() + ": Getting project…");
		Project project = project(localRepository, pomPath("com.github.ArloL", "newlinechecker", "133576b455"),
				Collections.emptyList(), remotes);
		System.out.println(System.currentTimeMillis() + ": Getting jars…");
		URL[] jars = getJarUrls(projectDependencies(project, project), localRepository, remotes);
		System.out.println(System.currentTimeMillis() + ": Running…");

		URLClassLoader classLoader = new URLClassLoader(jars, MavenExecutor.class.getClassLoader());
		Class<?> classToLoad = Class.forName(mainClass, true, classLoader);
		classToLoad.getMethod("main", new Class[] { args.getClass() }).invoke(null, new Object[] { args });
	}

	public static URL[] getJarUrls(Collection<Dependency> dependencies, Path localRepository,
			Collection<String> remotes) throws Exception {
		List<URL> result = new ArrayList<>();
		for (Dependency dependency : dependencies) {
			Path jarPath = jarPath(dependency);
			Path absoluteJarPath = localRepository.resolve(jarPath);

			URL url = null;
			if (Files.exists(absoluteJarPath)) {
				url = absoluteJarPath.toUri().toURL();
			} else {
				for (String remote : remotes) {
					URI pomUri = URI.create(remote).resolve(jarPath.toString());
					HttpRequest request = HttpRequest.newBuilder().uri(pomUri).method("HEAD", BodyPublishers.noBody())
							.timeout(Duration.ofMillis(TIMEOUT_MS)).build();
					HttpResponse<Void> response = HttpClient.newBuilder().build().send(request,
							HttpResponse.BodyHandlers.discarding());
					if (response.statusCode() == 200) {
						url = pomUri.toURL();
						break;
					}
				}
				if (url == null) {
					throw new IllegalArgumentException("Download failed " + jarPath);
				}
			}
			result.add(url);
		}
		return result.toArray(URL[]::new);
	}

	public static Collection<Dependency> projectDependencies(Project rootProject, Project project) throws Exception {
		Collection<Dependency> dependencies = new LinkedHashSet<>();
		if (rootProject == project) {
			Dependency dependency = new Dependency();
			dependency.groupId = project.groupId;
			dependency.artifactId = project.artifactId;
			dependency.version = project.version;
			dependencies.add(dependency);
		}
		if (project.parent != null) {
			projectDependencies(rootProject, project.parent.project).stream().forEach(dependencies::add);
		}
		for (Dependency dependency : project.dependencies) {
			if (!("test".equals(dependency.scope) || "provided".equals(dependency.scope) || dependency.optional)) {
				dependencies.add(dependency);
				projectDependencies(rootProject, dependency.project).stream().forEach(dependencies::add);
			}
		}
		return dependencies;
	}

	private static void manageDependency(List<Project> projects, Dependency dependency) {
		String version = null;
		String scope = null;
		for (Project project : projects) {
			Project searchProject = project;
			while (searchProject != null) {
				Optional<Dependency> findFirst = searchProject.dependencies.stream().filter(dependency::equalsArtifact)
						.findFirst();
				if (findFirst.isPresent()) {
					Dependency override = findFirst.get();
					if (version == null) {
						version = override.version;
					}
					if (scope == null) {
						scope = override.scope;
					}
				}
				if (searchProject.dependencyManagement != null) {
					findFirst = searchProject.dependencyManagement.dependencies.stream().filter(
							d -> d.groupId.equals(dependency.groupId) && d.artifactId.equals(dependency.artifactId))
							.findFirst();
					if (findFirst.isPresent()) {
						Dependency override = findFirst.get();
						if (version == null) {
							version = override.version;
						}
						if (scope == null) {
							scope = override.scope;
						}
					}
				}
				searchProject = Optional.ofNullable(searchProject.parent).map(p -> p.project).orElse(null);
			}
		}
		if (version != null) {
			dependency.version = version;
		}
		if (scope != null) {
			dependency.scope = scope;
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
			String localRepository = getTextContentFromFirstChildElementByTagName(
					xmlDocument(settingsXml).getDocumentElement(), "localRepository");
			if (localRepository != null && !localRepository.isBlank()) {
				return Paths.get(template(localRepository, Collections.emptyMap()));
			}
		}
		return userHomeM2.resolve("repository");
	}

	public static String template(String text, Map<?, ?> properties) {
		if (text == null) {
			return text;
		}
		Map<Object, Object> map = new HashMap<>(properties);
		System.getenv().forEach(map::putIfAbsent);
		System.getProperties().forEach(map::putIfAbsent);
		final Matcher mat = PROPERTIES_TOKEN.matcher(text);
		StringBuilder result = new StringBuilder();
		int last = 0;
		while (mat.find()) {
			result.append(text.substring(last, mat.start()));
			String key = mat.group(1);
			String lookup = key;
			if (lookup.startsWith("env.")) {
				lookup = lookup.substring("env.".length());
			}
			Object value = map.get(lookup);
			if (value != null) {
				result.append(value);
			} else {
				result.append("${").append(key).append("}");
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

	private static Document xmlDocument(byte[] xml) throws Exception {
		DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document document = documentBuilder.parse(new ByteArrayInputStream(xml));
		document.getDocumentElement().normalize();
		return document;
	}

	private static String getTextContentFromFirstChildElementByTagName(Element element, String tagName) {
		return Optional.of(getChildElementsByTagName(element, tagName)).filter(l -> !l.isEmpty())
				.map(l -> l.get(0).getTextContent()).orElse(null);
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

	public static Project project(Path localRepository, Path pom, List<Project> projects, Collection<String> remotes)
			throws Exception {
		Project project = new Project();
		projects = new ArrayList<>(projects);
		projects.add(project);

		Path absolutePomPath = localRepository.resolve(pom);

		Document xmlDocument = null;
		if (Files.exists(absolutePomPath)) {
			xmlDocument = xmlDocument(absolutePomPath);
		} else {
			System.out.println(System.currentTimeMillis() + ": downloading " + pom);
			for (String remote : remotes) {
				URI pomUri = URI.create(remote).resolve(pom.toString());
				HttpRequest request = HttpRequest.newBuilder().uri(pomUri).timeout(Duration.ofMillis(TIMEOUT_MS))
						.build();
				HttpResponse<byte[]> response = HttpClient.newBuilder().build().send(request,
						HttpResponse.BodyHandlers.ofByteArray());
				if (response.statusCode() == 200 && response.body().length > 0) {
					xmlDocument = xmlDocument(response.body());
					break;
				}
			}
			if (xmlDocument == null) {
				throw new IllegalArgumentException("Download failed " + pom);
			}
		}

		Element projectElement = xmlDocument.getDocumentElement();

		List<Element> parentElements = getChildElementsByTagName(projectElement, "parent");
		if (parentElements.size() == 1) {
			Dependency dependency = dependencyFromElement(parentElements.get(0));
			dependency.project = project(localRepository, pomPath(dependency), projects, remotes);
			project.parent = dependency;
		}

		project.artifactId = getTextContentFromFirstChildElementByTagName(projectElement, "artifactId");
		project.groupId = getTextContentFromFirstChildElementByTagName(projectElement, "groupId");
		project.version = getTextContentFromFirstChildElementByTagName(projectElement, "version");

		if (project.groupId == null) {
			project.groupId = project.parent.groupId;
		}
		if (project.version == null) {
			project.version = project.parent.version;
		}

		project.properties.put("project.version", project.version);

		List<Element> propertiesElements = getChildElementsByTagName(projectElement, "properties");
		if (!propertiesElements.isEmpty()) {
			NodeList propertyNodes = propertiesElements.get(0).getChildNodes();
			for (int i = 0; i < propertyNodes.getLength(); i++) {
				Node propertyNode = propertyNodes.item(i);
				if (propertyNode.getNodeType() == Node.ELEMENT_NODE) {
					Element propertyElement = (Element) propertyNode;
					project.properties.put(propertyElement.getTagName(), propertyElement.getTextContent());
				}
			}
		}

		List<Element> dependencyManagementElements = getChildElementsByTagName(projectElement, "dependencyManagement");
		if (!dependencyManagementElements.isEmpty()) {
			project.dependencyManagement = new DependencyManagement();
			NodeList dependencyElements = dependencyManagementElements.get(0).getElementsByTagName("dependency");
			for (int i = 0; i < dependencyElements.getLength(); i++) {
				Element dependencyElement = (Element) dependencyElements.item(i);
				Dependency dependency = dependencyFromElement(dependencyElement);
				dependency.version = template(dependency.version, project.properties);
				project.dependencyManagement.dependencies.add(dependency);
			}
		}

		List<Element> dependenciesElements = getChildElementsByTagName(projectElement, "dependencies");
		if (!dependenciesElements.isEmpty()) {
			List<Element> dependencyElements = getChildElementsByTagName(dependenciesElements.get(0), "dependency");
			for (Element dependencyElement : dependencyElements) {
				Dependency dependency = dependencyFromElement(dependencyElement);
				dependency.version = template(dependency.version, project.properties);
				project.dependencies.add(dependency);
			}
		}

		if (project.parent != null) {
			Project searchProject = project.parent.project;
			while (searchProject != null) {
				for (Dependency dependency : searchProject.dependencies) {
					String version = dependency.version;
					manageDependency(projects, dependency);
					if (!dependency.version.equals(version)) {
						dependency.project = project(localRepository, pomPath(dependency), projects, remotes);
					}
				}
				searchProject = Optional.ofNullable(searchProject.parent).map(p -> p.project).orElse(null);
			}
		}

		for (Dependency dependency : project.dependencies) {
			manageDependency(projects, dependency);
			dependency.project = project(localRepository, pomPath(dependency), projects, remotes);
		}

		return project;
	}

	private static Dependency dependencyFromElement(Element element) {
		Dependency depependency = new Dependency();
		depependency.groupId = getTextContentFromFirstChildElementByTagName(element, "groupId");
		depependency.artifactId = getTextContentFromFirstChildElementByTagName(element, "artifactId");

		String version = getTextContentFromFirstChildElementByTagName(element, "version");
		if (version != null) {
			depependency.version = version;
		}

		String scope = getTextContentFromFirstChildElementByTagName(element, "scope");
		if (scope != null) {
			depependency.scope = scope;
		}

		String classifier = getTextContentFromFirstChildElementByTagName(element, "classifier");
		if (classifier != null) {
			depependency.classifier = classifier;
		}

		String packaging = getTextContentFromFirstChildElementByTagName(element, "packaging");
		if (packaging != null) {
			depependency.packaging = packaging;
		}

		String optional = getTextContentFromFirstChildElementByTagName(element, "optional");
		if ("true".equals(optional)) {
			depependency.optional = true;
		}

		return depependency;
	}

	public static URL toURL(URI uri) {
		try {
			return uri.toURL();
		} catch (MalformedURLException e) {
			throw new UncheckedIOException(e);
		}
	}

}
