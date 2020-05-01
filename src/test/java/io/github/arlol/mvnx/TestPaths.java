package io.github.arlol.mvnx;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.core.io.ClassPathResource;

public class TestPaths {

	public static Path get(String path) throws IOException {
		return new ClassPathResource(path).getFile().toPath();
	}

}
