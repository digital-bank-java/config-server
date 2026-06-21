package com.digitalbank.configserver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileSystemUtils;

record GitTestRepository(Path directory, String revision) {

    static GitTestRepository create() {
        try {
            Path directory = Files.createTempDirectory(
                    "config-server-test-repository-").toRealPath();
            try (InputStream fixture = new ClassPathResource(
                    "config-repo/test-service.yml").getInputStream()) {
                Files.copy(fixture, directory.resolve("test-service.yml"));
            }

            try (Git git = Git.init()
                    .setDirectory(directory.toFile())
                    .setInitialBranch("main")
                    .call()) {

                git.add().addFilepattern(".").call();

                String revision = git.commit()
                        .setMessage("Add test configuration")
                        .setAuthor("Config Server Test", "test@digital-bank.local")
                        .setCommitter("Config Server Test", "test@digital-bank.local")
                        .call()
                        .getName();

                return new GitTestRepository(directory, revision);
            }
        } catch (IOException | GitAPIException exception) {
            throw new IllegalStateException("Failed to create Git test repository", exception);
        }
    }

    String uri() {
        return directory.toUri().toString();
    }

    void delete() throws IOException {
        FileSystemUtils.deleteRecursively(directory);
    }
}