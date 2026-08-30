package io.github.arlol.mvnx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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

	public static void main(String[] args) throws ClassNotFoundException,
			IllegalAccessException, InvocationTargetException,
			NoSuchMethodException, SecurityException {
		MavenExecutor mavenExecutor = new MavenExecutor();
		mavenExecutor.parseArguments(args);
		mavenExecutor.execute();
	}

	private static final Pattern PROPERTIES_TOKEN = Pattern
			.compile("\\$\\{([\\w.-]+)\\}");
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
				maven.settingsXml = Path.of(arguments[++i]);
				break;
			case "--localRepository":
				maven.localRepository = Path.of(arguments[++i]);
				break;
			case "--":
				passthroughArguments = Arrays
						.copyOfRange(arguments, i + 1, arguments.length);
				i = arguments.length;
				break;
			case "--saveToLocalRepository":
				maven.saveToLocalRepository = true;
				break;
			default:
				break;
			}
		}
		if (maven.localRepository == null) {
			maven.localRepository = maven
					.localRepository(maven.userHomeM2, maven.settingsXml);
		}
		return this;
	}

	public static boolean classPathFilter(Artifact artifact) {
		return !(artifact.packaging.equals("pom")
				|| "test".equals(artifact.scope)
				|| "provided".equals(artifact.scope) || artifact.optional);
	}

	public void execute() throws ClassNotFoundException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException,
			SecurityException {
		maven.resolve(artifact, List.of(), MavenExecutor::classPathFilter);
		if (mainClass == null) {
			mainClass = artifact.properties.get("mainClass");
		}
		if (mainClass == null) {
			mainClass = artifact.properties.get("start-class");
		}
		URL[] jars = getJarUrls(
				artifact.dependencies(MavenExecutor::classPathFilter)
		);
		URLClassLoader classLoader = new URLClassLoader(jars);
		Class<?> classToLoad = Class.forName(mainClass, true, classLoader);
		classToLoad
				.getMethod(
						"main",
						new Class[] { passthroughArguments.getClass() }
				)
				.invoke(null, new Object[] { passthroughArguments });
	}

	public URL[] getJarUrls(Collection<Artifact> dependencies) {
		return dependencies.stream()
				.map(dependency -> maven.uri(dependency, dependency.packaging))
				.map(uri -> {
					try {
						return uri.toURL();
					} catch (MalformedURLException e) {
						throw new UncheckedIOException(e);
					}
				})
				.toArray(URL[]::new);
	}

	public static class Maven {

		Path userHomeM2 = userHomeM2(Path.of(System.getProperty("user.home")));
		Path settingsXml = settingsXml(userHomeM2);
		Path localRepository;
		boolean saveToLocalRepository = false;
		Collection<String> repositories = List.of(
				"https://repo.maven.apache.org/maven2/",
				"https://jitpack.io/"
		);

		private DocumentBuilder documentBuilder;

		public Document xmlDocument(URI uri) {
			try {
				if (documentBuilder == null) {
					documentBuilder = DocumentBuilderFactory.newInstance()
							.newDocumentBuilder();
				}
				return documentBuilder
						.parse(new InputSource(uri.toASCIIString()));
			} catch (SAXException | ParserConfigurationException
					| IOException e) {
				throw new IllegalStateException(e);
			}
		}

		public Document pom(Artifact artifact) {
			Path path = path(artifact, "pom");
			Path absolutePath = localRepository.resolve(path);
			if (Files.exists(absolutePath)) {
				return xmlDocument(absolutePath.toUri());
			}
			return xmlDocument(uri(artifact, "pom"));
		}

		public URI uri(Artifact artifact, String extension) {
			Path path = Maven.path(artifact, extension);
			Path absolutePath = localRepository.resolve(path);
			if (Files.exists(absolutePath)) {
				return absolutePath.toUri();
			}
			if (artifact.remote != null) {
				URI uri = uri(artifact.remote, path, absolutePath);
				if (uri != null) {
					return uri;
				}
			}
			for (String remote : repositories) {
				URI uri = uri(remote, path, absolutePath);
				if (uri != null) {
					artifact.remote = remote;
					return uri;
				}
			}
			throw new IllegalArgumentException("Download failed " + path);
		}

		public URI uri(String remote, Path path, Path absolutePath) {
			try {
				URI uri = URI.create(remote)
						.resolve(path.toString().replace('\\', '/'));
				HttpClient httpClient = HttpClient.newBuilder()
						.followRedirects(HttpClient.Redirect.NORMAL)
						.build();
				Builder requestBuilder = HttpRequest.newBuilder()
						.uri(uri)
						.timeout(Duration.ofMillis(TIMEOUT_MS));
				if (saveToLocalRepository) {
					Path parent = absolutePath.getParent();
					if (parent != null) {
						Files.createDirectories(parent);
					}
					HttpResponse<Path> response = httpClient.send(
							requestBuilder.build(),
							HttpResponse.BodyHandlers.ofFile(absolutePath)
					);
					if (response.statusCode() == 200) {
						return response.body().toUri();
					}
				} else {
					HttpRequest request = requestBuilder
							.method("HEAD", BodyPublishers.noBody())
							.build();
					HttpResponse<Void> response = httpClient.send(
							request,
							HttpResponse.BodyHandlers.discarding()
					);
					if (response.statusCode() == 200) {
						return uri;
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
			return null;
		}

		public Path localRepository(Path userHomeM2, Path settingsXml) {
			if (Files.exists(settingsXml)) {
				NodeList elements = xmlDocument(settingsXml.toUri())
						.getElementsByTagName("localRepository");
				if (elements.getLength() > 0
						&& !elements.item(0).getTextContent().isBlank()) {
					return Path.of(
							template(
									elements.item(0).getTextContent(),
									Maven::lookupSystemPropertyOrEnvironmentVariable
							)
					);
				}
			}
			return userHomeM2.resolve("repository");
		}

		public void resolve(
				Artifact artifact,
				List<Artifact> artifacts,
				Predicate<Artifact> filter
		) {
			List<Artifact> artifactHierarchy = new ArrayList<>(artifacts);
			artifactHierarchy.add(artifact);

			NodeList projectNodes = pom(artifact).getDocumentElement()
					.getChildNodes();
			for (int i = 0; i < projectNodes.getLength(); i++) {
				readProjectNode(
						artifact,
						projectNodes.item(i),
						artifactHierarchy,
						filter
				);
			}

			resolveDependencies(artifact, artifactHierarchy, filter);
		}

		private void readProjectNode(
				Artifact artifact,
				Node item,
				List<Artifact> artifactHierarchy,
				Predicate<Artifact> filter
		) {
			switch (item.getNodeName()) {
			case "parent":
				readParent(artifact, item, artifactHierarchy, filter);
				break;
			case "groupId":
				artifact.groupId = item.getTextContent();
				break;
			case "artifactId":
				artifact.artifactId = item.getTextContent();
				break;
			case "version":
				artifact.version = item.getTextContent();
				artifact.properties.put("project.version", artifact.version);
				break;
			case "packaging":
				artifact.packaging = item.getTextContent();
				break;
			case "properties":
				readProperties(artifact, item);
				break;
			case "dependencyManagement":
				readDependencyManagement(
						artifact,
						item,
						artifactHierarchy,
						filter
				);
				break;
			case "dependencies":
				readDependencies(artifact, item);
				break;
			default:
				// any other element is irrelevant for resolution
			}
		}

		private void readParent(
				Artifact artifact,
				Node item,
				List<Artifact> artifactHierarchy,
				Predicate<Artifact> filter
		) {
			Artifact parent = artifactFromNode(item);
			parent.packaging = "pom";
			resolve(parent, artifactHierarchy, filter);
			artifact.parent = parent;
			if (artifact.version == null) {
				artifact.version = parent.version;
				artifact.properties.put("project.version", artifact.version);
			}
			if (artifact.groupId == null) {
				artifact.groupId = parent.groupId;
			}
		}

		private static void readProperties(Artifact artifact, Node item) {
			NodeList propertyNodes = item.getChildNodes();
			for (int i = 0; i < propertyNodes.getLength(); i++) {
				Node propertyNode = propertyNodes.item(i);
				if (propertyNode.getNodeType() != Node.ELEMENT_NODE) {
					continue;
				}
				Element propertyElement = (Element) propertyNode;
				artifact.properties.put(
						propertyElement.getTagName(),
						propertyElement.getTextContent()
				);
			}
		}

		private void readDependencyManagement(
				Artifact artifact,
				Node item,
				List<Artifact> artifactHierarchy,
				Predicate<Artifact> filter
		) {
			NodeList dependencyElements = ((Element) item)
					.getElementsByTagName("dependency");
			for (int i = 0; i < dependencyElements.getLength(); i++) {
				Artifact dependency = artifactFromNode(
						dependencyElements.item(i)
				);
				resolveProperties(
						dependency,
						key -> lookupProperty(key, artifactHierarchy)
				);
				if ("import".equals(dependency.scope)) {
					resolve(dependency, artifactHierarchy, filter);
					artifact.dependencyManagement
							.addAll(dependency.dependencyManagement);
				} else {
					artifact.dependencyManagement.add(dependency);
				}
			}
		}

		private static void readDependencies(Artifact artifact, Node item) {
			NodeList dependencyNodes = item.getChildNodes();
			for (int i = 0; i < dependencyNodes.getLength(); i++) {
				Node dependencyNode = dependencyNodes.item(i);
				if (dependencyNode.getNodeType() != Node.ELEMENT_NODE) {
					continue;
				}
				artifact.dependencies.add(artifactFromNode(dependencyNode));
			}
		}

		private void resolveDependencies(
				Artifact artifact,
				List<Artifact> artifactHierarchy,
				Predicate<Artifact> filter
		) {
			for (Artifact dependency : artifact.dependencies) {
				if (!filter.test(dependency)) {
					continue;
				}
				resolveProperties(
						dependency,
						key -> lookupProperty(key, artifactHierarchy)
				);
				if (!filter.test(dependency)) {
					continue;
				}
				manage(dependency, artifactHierarchy);
				if (!filter.test(dependency)) {
					continue;
				}
				resolve(dependency, artifactHierarchy, filter);
			}
		}

		public static void manage(
				Artifact artifact,
				List<Artifact> dependents
		) {
			String version = null;
			String scope = null;
			List<Artifact> dependencies = dependents.stream()
					.flatMap(dependent -> dependent.hierarchy().stream())
					.flatMap(
							dependent -> Stream.concat(
									dependent.dependencies.stream(),
									dependent.dependencyManagement.stream()
							)
					)
					.filter(artifact::equalsArtifact)
					.collect(Collectors.toList());
			for (Artifact dependency : dependencies) {
				if (version == null) {
					version = dependency.version;
				}
				if (scope == null) {
					scope = dependency.scope;
				}
				if (version != null && scope != null) {
					break;
				}
			}
			if (version != null) {
				artifact.version = version;
			}
			if (scope == null) {
				scope = "compile";
			}
			artifact.scope = scope;
		}

		public static Path userHomeM2(Path userHome) {
			return userHome.resolve(".m2");
		}

		public static Path settingsXml(Path userHomeM2) {
			return userHomeM2.resolve("settings.xml");
		}

		public static String lookupSystemPropertyOrEnvironmentVariable(
				String key
		) {
			return Optional.ofNullable(System.getProperty(key))
					.orElseGet(
							() -> System.getenv(key.substring("env.".length()))
					);
		}

		public static String lookupProperty(
				String key,
				List<Artifact> artifacts
		) {
			for (Artifact artifact : artifacts) {
				String value = artifact.properties.get(key);
				if (value != null) {
					return value;
				}
				if (artifact.parent != null) {
					value = lookupProperty(key, List.of(artifact.parent));
					if (value != null) {
						return value;
					}
				}
			}
			return lookupSystemPropertyOrEnvironmentVariable(key);
		}

		public static void resolveProperties(
				Artifact artifact,
				Function<String, String> lookupFunction
		) {
			artifact.groupId = template(artifact.groupId, lookupFunction);
			artifact.artifactId = template(artifact.artifactId, lookupFunction);
			artifact.version = template(artifact.version, lookupFunction);
			artifact.classifier = template(artifact.classifier, lookupFunction);
			artifact.packaging = template(artifact.packaging, lookupFunction);
			artifact.scope = template(artifact.scope, lookupFunction);
		}

		public static String template(
				String text,
				Function<String, String> lookupFunction
		) {
			if (text == null) {
				return text;
			}

			final Matcher mat = PROPERTIES_TOKEN.matcher(text);
			StringBuilder builder = new StringBuilder();
			int last = 0;
			while (mat.find()) {
				builder.append(text.substring(last, mat.start()));
				String key = mat.group(1);
				String value = lookupFunction.apply(key);
				value = template(value, lookupFunction);
				if (value != null) {
					builder.append(value);
				} else {
					builder.append("${").append(key).append("}");
				}
				last = mat.end();
			}

			if (last == 0) {
				return text;
			}

			builder.append(text.substring(last));

			return builder.toString();
		}

		private static Artifact artifactFromNode(Node node) {
			Artifact artifact = new Artifact();
			for (int i = 0; i < node.getChildNodes().getLength(); i++) {
				Node item = node.getChildNodes().item(i);
				switch (item.getNodeName()) {
				case "groupId":
					artifact.groupId = item.getTextContent();
					break;
				case "artifactId":
					artifact.artifactId = item.getTextContent();
					break;
				case "version":
					artifact.version = item.getTextContent();
					break;
				case "scope":
					artifact.scope = item.getTextContent();
					break;
				case "classifier":
					artifact.classifier = item.getTextContent();
					break;
				case "type":
					artifact.packaging = item.getTextContent();
					break;
				case "optional":
					artifact.optional = Boolean
							.parseBoolean(item.getTextContent());
					break;
				default:
					// ignore
				}
			}
			return artifact;
		}

		public static Path path(Artifact dependency, String extension) {
			if (dependency.groupId == null) {
				throw new IllegalStateException("groupId is null");
			}
			return Path.of(dependency.groupId.replace(".", "/"))
					.resolve(dependency.artifactId)
					.resolve(dependency.version)
					.resolve(
							dependency.artifactId + "-" + dependency.version
									+ "." + extension
					);
		}

	}

	public static class Artifact {

		Artifact parent;
		String groupId;
		String artifactId;
		String version;
		String packaging = "jar";
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

		public boolean equalsArtifact(Artifact other) {
			return Objects.equals(artifactId, other.artifactId)
					&& Objects.equals(classifier, other.classifier)
					&& Objects.equals(groupId, other.groupId)
					&& Objects.equals(packaging, other.packaging);
		}

		@Override
		public int hashCode() {
			return Objects
					.hash(artifactId, classifier, groupId, packaging, version);
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
			return Objects.equals(artifactId, other.artifactId)
					&& Objects.equals(classifier, other.classifier)
					&& Objects.equals(groupId, other.groupId)
					&& Objects.equals(packaging, other.packaging)
					&& Objects.equals(version, other.version);
		}

		@Override
		public String toString() {
			return "Dependency [groupId=" + groupId + ", artifactId="
					+ artifactId + ", version=" + version + ", packaging="
					+ packaging + ", classifier=" + classifier + ", scope="
					+ scope + "]";
		}

	}

}
