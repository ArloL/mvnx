package io.github.arlol.mvnx;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class MavenExecutor {

	public static void main(String[] args) throws Exception {
		MavenExecutor mavenExecutor = new MavenExecutor();
		mavenExecutor.parseArguments(args);
		mavenExecutor.execute();
	}

	private static final Pattern PROPERTIES_TOKEN = Pattern.compile("\\$\\{([\\w.-]+)\\}");
	private static final long TIMEOUT_MS = 10_000;

	Maven maven = new Maven();
	Artifact artifact = new Artifact();
	String mainClass;
	String[] passthroughArguments = new String[0];

	public MavenExecutor parseArguments(String[] arguments) {
		if (arguments.length == 0) {
			throw new IllegalArgumentException("Missing artifact identifier");
		}
		String[] identifier = arguments[0].split(":");
		artifact.groupId = identifier[0];
		artifact.artifactId = identifier[1];
		artifact.version = identifier[2];
		for (int i = 1; i < arguments.length; i++) {
			switch (arguments[i]) {
			case "--repositories":
				maven.repositories = Arrays.asList(arguments[++i].split(","));
				break;
			case "--mainClass":
				mainClass = arguments[++i];
				break;
			case "--settings":
				maven.settingsXml = Paths.get(arguments[++i]);
				break;
			case "--localRepository":
				maven.localRepository = Paths.get(arguments[++i]);
				break;
			case "--":
				passthroughArguments = Arrays.copyOfRange(arguments, i + 1, arguments.length);
				i = arguments.length;
				break;
			default:
				break;
			}
		}
		if (maven.localRepository == null) {
			maven.localRepository = maven.localRepository(maven.userHomeM2, maven.settingsXml);
		}
		return this;
	}

	public void execute() throws Exception {
		maven.resolve(artifact, Collections.emptyList());
		if (mainClass == null) {
			mainClass = artifact.properties.get("mainClass");
		}
		URL[] jars = getJarUrls(artifact.dependencies(dependency -> !(dependency.packaging.equals("pom")
				|| "test".equals(dependency.scope) || "provided".equals(dependency.scope) || dependency.optional)));
		URLClassLoader classLoader = new URLClassLoader(jars);
		Class<?> classToLoad = Class.forName(mainClass, true, classLoader);
		classToLoad.getMethod("main", new Class[] { passthroughArguments.getClass() }).invoke(null,
				new Object[] { passthroughArguments });
	}

	public URL[] getJarUrls(Collection<Artifact> dependencies) throws Exception {
		List<URL> result = new ArrayList<>();
		for (Artifact dependency : dependencies) {
			Path jarPath = Maven.path(dependency);
			Path absoluteJarPath = maven.localRepository.resolve(jarPath);

			URL url = null;
			if (Files.exists(absoluteJarPath)) {
				url = absoluteJarPath.toUri().toURL();
			} else {
				if (dependency.remote != null) {
					URI pomUri = URI.create(dependency.remote).resolve(jarPath.toString().replace('\\', '/'));
					HttpRequest request = HttpRequest.newBuilder().uri(pomUri).method("HEAD", BodyPublishers.noBody())
							.timeout(Duration.ofMillis(TIMEOUT_MS)).build();
					HttpResponse<Void> response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS)
							.build().send(request, HttpResponse.BodyHandlers.discarding());
					if (response.statusCode() == 200) {
						url = pomUri.toURL();
					}
				}
				if (url == null) {
					for (String remote : maven.repositories) {
						URI pomUri = URI.create(remote).resolve(jarPath.toString().replace('\\', '/'));
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

	public static class Maven {

		Path userHomeM2 = userHomeM2(Paths.get(System.getProperty("user.home")));
		Path settingsXml = settingsXml(userHomeM2);
		Path localRepository;
		boolean inMemory = true;
		Collection<String> repositories = List.of("https://repo.maven.apache.org/maven2/", "https://jitpack.io/");

		public Document xmlDocument(InputSource inputSource) {
			try {
				DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
				Document document = documentBuilder.parse(inputSource);
				document.getDocumentElement().normalize();
				return document;
			} catch (SAXException | ParserConfigurationException | IOException e) {
				throw new IllegalStateException(e);
			}
		}

		public Document xmlDocument(Path path) {
			try (InputStream inputStream = Files.newInputStream(path)) {
				return xmlDocument(new InputSource(inputStream));
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		public Document pom(Artifact artifact) {
			Path path = path(artifact, "pom");
			Path absolutePath = localRepository.resolve(path);
			if (Files.exists(absolutePath)) {
				return xmlDocument(absolutePath);
			}
			for (String remote : repositories) {
				URI uri = URI.create(remote).resolve(path.toString().replace('\\', '/'));
				try {
					HttpRequest request = HttpRequest.newBuilder().uri(uri).timeout(Duration.ofMillis(TIMEOUT_MS))
							.build();
					HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
					if (inMemory) {
						HttpResponse<byte[]> response = httpClient.send(request,
								HttpResponse.BodyHandlers.ofByteArray());
						if (response.statusCode() == 200) {
							return xmlDocument(new InputSource(new ByteArrayInputStream(response.body())));
						}
					} else {
						Files.createDirectories(absolutePath.getParent());
						HttpResponse<Path> response = httpClient.send(request,
								HttpResponse.BodyHandlers.ofFile(absolutePath));
						if (response.statusCode() == 200) {
							return xmlDocument(response.body());
						}
					}
				} catch (IOException | InterruptedException e) {
					throw new IllegalStateException(e);
				}
			}
			throw new IllegalArgumentException("Download failed " + path);
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

		public void resolve(Artifact artifact, List<Artifact> artifacts) {
			artifacts = new ArrayList<>(artifacts);
			artifacts.add(artifact);

			Document xmlDocument = pom(artifact);

			Element projectElement = xmlDocument.getDocumentElement();

			List<Element> parentElements = getChildElementsByTagName(projectElement, "parent");
			if (parentElements.size() == 1) {
				Artifact parent = artifactFromElement(parentElements.get(0));
				parent.packaging = "pom";
				resolve(parent, artifacts);
				artifact.parent = parent;
			}

			artifact.artifactId = getTextContentFromFirstChildElementByTagName(projectElement, "artifactId");
			artifact.groupId = getTextContentFromFirstChildElementByTagName(projectElement, "groupId");
			artifact.version = getTextContentFromFirstChildElementByTagName(projectElement, "version");
			artifact.packaging = getTextContentFromFirstChildElementByTagName(projectElement, "packaging");

			if (artifact.groupId == null) {
				artifact.groupId = artifact.parent.groupId;
			}
			if (artifact.version == null) {
				artifact.version = artifact.parent.version;
			}
			if (artifact.packaging == null) {
				artifact.packaging = "jar";
			}

			artifact.properties.put("project.version", artifact.version);

			List<Element> propertiesElements = getChildElementsByTagName(projectElement, "properties");
			if (!propertiesElements.isEmpty()) {
				NodeList propertyNodes = propertiesElements.get(0).getChildNodes();
				for (int i = 0; i < propertyNodes.getLength(); i++) {
					Node propertyNode = propertyNodes.item(i);
					if (propertyNode.getNodeType() == Node.ELEMENT_NODE) {
						Element propertyElement = (Element) propertyNode;
						artifact.properties.put(propertyElement.getTagName(), propertyElement.getTextContent());
					}
				}
			}

			List<Element> dependencyManagementElements = getChildElementsByTagName(projectElement,
					"dependencyManagement");
			if (!dependencyManagementElements.isEmpty()) {
				NodeList dependencyElements = dependencyManagementElements.get(0).getElementsByTagName("dependency");
				for (int i = 0; i < dependencyElements.getLength(); i++) {
					Element dependencyElement = (Element) dependencyElements.item(i);
					Artifact dependency = artifactFromElement(dependencyElement);
					dependency.version = template(dependency.version, artifact.allProperties(artifacts));
					if ("import".equals(dependency.scope)) {
						resolve(dependency, artifacts);
						artifact.dependencyManagement.addAll(dependency.dependencyManagement);
					} else {
						artifact.dependencyManagement.add(dependency);
					}
				}
			}

			List<Element> dependenciesElements = getChildElementsByTagName(projectElement, "dependencies");
			if (!dependenciesElements.isEmpty()) {
				List<Element> dependencyElements = getChildElementsByTagName(dependenciesElements.get(0), "dependency");
				for (Element dependencyElement : dependencyElements) {
					Artifact dependency = artifactFromElement(dependencyElement);
					dependency.version = template(dependency.version, artifact.allProperties(artifacts));
					artifact.dependencies.add(dependency);
				}
			}

			if (artifact.parent != null) {
				Artifact searchArtifact = artifact.parent;
				while (searchArtifact != null) {
					for (Artifact dependency : searchArtifact.dependencies) {
						String version = dependency.version;
						dependency.manage(artifacts);
						if (!dependency.version.equals(version)) {
							resolve(dependency, artifacts);
						}
					}
					searchArtifact = searchArtifact.parent;
				}
			}

			for (Artifact dependency : artifact.dependencies) {
				dependency.manage(artifacts);
				resolve(dependency, artifacts);
			}
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

			String input;
			String output = text;

			do {
				input = output;

				final Matcher mat = PROPERTIES_TOKEN.matcher(input);
				StringBuilder builder = new StringBuilder();
				int last = 0;
				while (mat.find()) {
					builder.append(input.substring(last, mat.start()));
					String key = mat.group(1);
					String lookup = key;
					if (lookup.startsWith("env.")) {
						lookup = lookup.substring("env.".length());
					}
					Object value = map.get(lookup);
					if (value != null) {
						builder.append(value);
					} else {
						builder.append("${").append(key).append("}");
					}
					last = mat.end();
				}
				builder.append(input.substring(last));
				output = builder.toString();

			} while (!output.equals(input));

			return output;
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

		private static Artifact artifactFromElement(Element element) {
			Artifact artifact = new Artifact();
			artifact.groupId = getTextContentFromFirstChildElementByTagName(element, "groupId");
			artifact.artifactId = getTextContentFromFirstChildElementByTagName(element, "artifactId");

			String version = getTextContentFromFirstChildElementByTagName(element, "version");
			if (version != null) {
				artifact.version = version;
			}

			String scope = getTextContentFromFirstChildElementByTagName(element, "scope");
			if (scope != null) {
				artifact.scope = scope;
			}

			String classifier = getTextContentFromFirstChildElementByTagName(element, "classifier");
			if (classifier != null) {
				artifact.classifier = classifier;
			}

			String type = getTextContentFromFirstChildElementByTagName(element, "type");
			if (type != null) {
				artifact.packaging = type;
			} else {
				artifact.packaging = "jar";
			}

			String optional = getTextContentFromFirstChildElementByTagName(element, "optional");
			if ("true".equals(optional)) {
				artifact.optional = true;
			}

			return artifact;
		}

		public static Path path(Artifact dependency) {
			return path(dependency, dependency.packaging);
		}

		public static Path path(Artifact dependency, String extension) {
			return Paths.get(dependency.groupId.replace(".", "/")).resolve(dependency.artifactId)
					.resolve(dependency.version)
					.resolve(dependency.artifactId + "-" + dependency.version + "." + extension);
		}

	}

	public static class Artifact {

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
			String version = null;
			String scope = null;
			List<Artifact> artifacts = dependents.stream().flatMap(artifact -> artifact.hierarchy().stream()).flatMap(
					artifact -> Stream.concat(artifact.dependencies.stream(), artifact.dependencyManagement.stream()))
					.filter(this::equalsArtifact).collect(Collectors.toList());
			for (Artifact artifact : artifacts) {
				if (version == null) {
					version = artifact.version;
				}
				if (scope == null) {
					scope = artifact.scope;
				}
				if (version != null && scope != null) {
					break;
				}
			}
			if (version != null) {
				this.version = version;
			}
			if (scope == null) {
				scope = "compile";
			}
			this.scope = scope;
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
			return "Dependency [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version
					+ ", packaging=" + packaging + ", classifier=" + classifier + ", scope=" + scope + "]";
		}

	}

}
