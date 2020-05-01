package io.github.arlol.mvnx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class MavenExecutor {

	private static final Pattern PROPERTIES_TOKEN = Pattern.compile("\\$\\{([\\w.-]+)\\}");
	private static final long TIMEOUT_MS = 10_000;

	Dependency dependency = new Dependency();
	String mainClass;
	String[] passthroughArguments = new String[0];
	Collection<String> repositories = List.of("https://repo.maven.apache.org/maven2/", "https://jitpack.io/");
	Path userHomeM2 = userHomeM2(userHome());
	Path settingsXml = settingsXml(userHomeM2);
	Path localRepository;

	public MavenExecutor parseArguments(String[] arguments) {
		if (arguments.length == 0) {
			throw new IllegalArgumentException("Missing artifact identifier");
		}
		String[] identifier = arguments[0].split(":");
		dependency.groupId = identifier[0];
		dependency.artifactId = identifier[1];
		dependency.version = identifier[2];
		for (int i = 1; i < arguments.length; i++) {
			switch (arguments[i]) {
			case "--repositories":
				repositories = Arrays.asList(arguments[++i].split(","));
				break;
			case "--mainClass":
				mainClass = arguments[++i];
				break;
			case "--settings":
				settingsXml = Paths.get(arguments[++i]);
				break;
			case "--localRepository":
				localRepository = Paths.get(arguments[++i]);
				break;
			case "--":
				passthroughArguments = Arrays.copyOfRange(arguments, i + 1, arguments.length);
				i = arguments.length;
				break;
			default:
				break;
			}
		}
		if (localRepository == null) {
			localRepository = localRepository(userHomeM2, settingsXml);
		}
		return this;
	}

	public Path localRepository(Path userHomeM2, Path settingsXml) {
		if (Files.exists(settingsXml)) {
			String localRepository = getTextContentFromFirstChildElementByTagName(
					xmlDocument(settingsXml).getDocumentElement(), "localRepository");
			if (localRepository != null && !localRepository.isBlank()) {
				return Paths.get(template(localRepository, Collections.emptyMap()));
			}
		}
		return userHomeM2.resolve("repository");
	}

	public void parseProject() {
		dependency.project = project(dependency, Collections.emptyList());
	}

	public Project project(Dependency dep, List<Project> projects) {
		Project project = new Project();
		projects = new ArrayList<>(projects);
		projects.add(project);

		Document xmlDocument = xmlDocument(pomPath(dep));

		Element projectElement = xmlDocument.getDocumentElement();

		List<Element> parentElements = getChildElementsByTagName(projectElement, "parent");
		if (parentElements.size() == 1) {
			Dependency dependency = dependencyFromElement(parentElements.get(0));
			dependency.type = "pom";
			dependency.project = project(dependency, projects);
			project.parent = dependency;
		}

		project.artifactId = getTextContentFromFirstChildElementByTagName(projectElement, "artifactId");
		project.groupId = getTextContentFromFirstChildElementByTagName(projectElement, "groupId");
		project.version = getTextContentFromFirstChildElementByTagName(projectElement, "version");
		project.packaging = getTextContentFromFirstChildElementByTagName(projectElement, "packaging");

		if (project.groupId == null) {
			project.groupId = project.parent.groupId;
		}
		if (project.version == null) {
			project.version = project.parent.version;
		}
		if (project.packaging == null) {
			project.packaging = "jar";
		}
		dep.type = project.packaging;

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
				dependency.type = "jar";
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
						dependency.project = project(dependency, projects);
					}
				}
				searchProject = Optional.ofNullable(searchProject.parent).map(p -> p.project).orElse(null);
			}
		}

		for (Dependency dependency : project.dependencies) {
			manageDependency(projects, dependency);
			dependency.project = project(dependency, projects);
		}

		return project;
	}

	public Document xmlDocument(Path path) {
		Path absolutePath = path;
		if (!Files.exists(path)) {
			absolutePath = localRepository.resolve(path);
		}
		if (Files.exists(absolutePath)) {
			try {
				DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
				Document document = documentBuilder.parse(absolutePath.toFile());
				document.getDocumentElement().normalize();
				return document;
			} catch (SAXException | IOException | ParserConfigurationException e) {
				throw new IllegalStateException(e);
			}
		} else {
			for (String remote : repositories) {
				URI pomUri = URI.create(remote).resolve(path.toString());
				try {
					HttpRequest request = HttpRequest.newBuilder().uri(pomUri).timeout(Duration.ofMillis(TIMEOUT_MS))
							.build();
					HttpResponse<byte[]> response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
							.build().send(request, HttpResponse.BodyHandlers.ofByteArray());
					if (response.statusCode() == 200 && response.body().length > 0) {
						DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
						Document document = documentBuilder.parse(new ByteArrayInputStream(response.body()));
						document.getDocumentElement().normalize();
						return document;
					}
				} catch (SAXException | IOException | ParserConfigurationException | InterruptedException e) {
					throw new IllegalStateException(e);
				}
			}
			throw new IllegalArgumentException("Download failed " + path);
		}
	}

	public static void main(String[] args) throws Exception {
		MavenExecutor mavenExecutor = new MavenExecutor();
		mavenExecutor.parseArguments(args);
		mavenExecutor.parseProject();
		if (mavenExecutor.mainClass == null) {
			mavenExecutor.mainClass = mavenExecutor.dependency.project.properties.get("mainClass");
		}
		URL[] jars = getJarUrls(
				mavenExecutor.dependency
						.dependencies(dependency -> !(dependency.type.equals("pom") || "test".equals(dependency.scope)
								|| "provided".equals(dependency.scope) || dependency.optional)),
				mavenExecutor.localRepository, mavenExecutor.repositories);
		URLClassLoader classLoader = new URLClassLoader(jars, MavenExecutor.class.getClassLoader());
		Class<?> classToLoad = Class.forName(mavenExecutor.mainClass, true, classLoader);
		classToLoad.getMethod("main", new Class[] { mavenExecutor.passthroughArguments.getClass() }).invoke(null,
				new Object[] { mavenExecutor.passthroughArguments });
	}

	public static URL[] getJarUrls(Collection<Dependency> dependencies, Path localRepository,
			Collection<String> remotes) throws Exception {
		List<URL> result = new ArrayList<>();
		for (Dependency dependency : dependencies) {
			Path jarPath = jarPath(dependency);
			Path absoluteJarPath = localRepository.resolve(jarPath);

			URL url = null;
			if (Files.exists(absoluteJarPath)) {
				url = toURL(absoluteJarPath.toUri());
			} else {
				if (dependency.project.remote != null) {
					URI pomUri = URI.create(dependency.project.remote).resolve(jarPath.toString());
					HttpRequest request = HttpRequest.newBuilder().uri(pomUri).method("HEAD", BodyPublishers.noBody())
							.timeout(Duration.ofMillis(TIMEOUT_MS)).build();
					HttpResponse<Void> response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
							.build().send(request, HttpResponse.BodyHandlers.discarding());
					if (response.statusCode() == 200) {
						url = pomUri.toURL();
					}
				}
				if (url == null) {
					for (String remote : remotes) {
						URI pomUri = URI.create(remote).resolve(jarPath.toString());
						HttpRequest request = HttpRequest.newBuilder().uri(pomUri)
								.method("HEAD", BodyPublishers.noBody()).timeout(Duration.ofMillis(TIMEOUT_MS)).build();
						HttpResponse<Void> response = HttpClient.newBuilder()
								.followRedirects(HttpClient.Redirect.ALWAYS).build()
								.send(request, HttpResponse.BodyHandlers.discarding());
						if (response.statusCode() == 200) {
							url = pomUri.toURL();
							break;
						}
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
		if (scope == null) {
			scope = "compile";
		}
		dependency.scope = scope;
	}

	public static Path jarPath(Dependency dependency) {
		return Paths.get(dependency.groupId.replace(".", "/")).resolve(dependency.artifactId)
				.resolve(dependency.version).resolve(dependency.artifactId + "-" + dependency.version + ".jar");
	}

	public static Path pomPath(Dependency dependency) {
		return Paths.get(dependency.groupId.replace(".", "/")).resolve(dependency.artifactId)
				.resolve(dependency.version).resolve(dependency.artifactId + "-" + dependency.version + ".pom");
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

		String type = getTextContentFromFirstChildElementByTagName(element, "type");
		if (type != null) {
			depependency.type = type;
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
