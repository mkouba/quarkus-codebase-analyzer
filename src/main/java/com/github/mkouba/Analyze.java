package com.github.mkouba;

import static java.util.function.Predicate.not;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.jboss.logging.Logger;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import io.quarkus.runtime.annotations.ConfigItem;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "analyze", mixinStandardHelpOptions = true)
public class Analyze implements Runnable {

    private static final Logger LOG = Logger.getLogger(Analyze.class);

    @Option(names = { "-c",
            "--clear" }, description = "Clear the working dir and clone the repo")
    boolean clear = false;

    @Option(names = { "-o", "--organization" }, description = "GitHub organization")
    String organization = "quarkusio";

    @Option(names = { "-r", "--repository" }, description = "GitHub repository")
    String repository = "quarkus";

    @Option(names = { "--tags" }, description = "Comma-separated tags to analyze. If not specified then all tags are analyzed.")
    String tags;

    @Option(names = { "-w", "--work-dir" }, description = "Working directory")
    String workDirPath;

    @Override
    public void run() {
        long start = System.nanoTime();
        Path workDir = workDirPath != null ? new File(workDirPath).toPath() : Paths.get("work");
        Path repoDir = workDir.resolve(repository);
        Path reportsDir = workDir.resolve("reports");
        boolean workDirExists = Files.exists(workDir);

        LOG.infof("Working directory: %s", workDirExists ? workDir.toAbsolutePath() : workDir);

        try {
            if (clear || !workDirExists) {
                // Delete if exists
                if (workDirExists) {
                    Files.walk(workDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
                // Clone the repo
                String remoteRepoName = "https://github.com/" + organization + "/" + repository + ".git";
                LOG.infof("Clone %s", remoteRepoName);
                CloneCommand clone = Git.cloneRepository()
                        .setURI(remoteRepoName)
                        .setDirectory(repoDir.toFile());
                long cloneStart = System.nanoTime();
                clone.call();
                LOG.infof("Cloned into %s in %s s", repoDir.toAbsolutePath(),
                        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - cloneStart));
            }

            List<Result> results;
            Git git = Git.open(repoDir.toFile());
            List<Ref> foundTags = git.tagList().call();
            LOG.infof("Found %s tags in the repository", foundTags.size());

            results = new ArrayList<>();
            List<String> tags = this.tags != null ? Arrays.asList(this.tags.split(",")) : Collections.emptyList();

            if (tags.isEmpty()) {
                LOG.infof("Going to analyze tags: %s",
                        foundTags.stream().map(Ref::getName).map(Repository::shortenRefName).collect(Collectors.toList()));
                for (Ref foundTag : foundTags) {
                    results.add(analyze(repoDir, git, foundTag));
                }
            } else {
                for (String tag : tags) {
                    LOG.infof("Going to analyze tags: %s", tags);
                    Optional<Ref> foundTag = foundTags.stream()
                            .filter(ref -> Repository.shortenRefName(ref.getName()).equals(tag))
                            .findFirst();
                    if (foundTag.isEmpty()) {
                        throw new IllegalStateException("Tag not found: " + tag);
                    }
                    results.add(analyze(repoDir, git, foundTag.get()));
                }
            }

            // Sort the results
            Collections.sort(results);

            // Render reports
            if (!Files.exists(reportsDir)) {
                Files.createDirectories(reportsDir);
            }
            Files.write(reportsDir.resolve("report.html"),
                    Templates.report(results).render().getBytes(StandardCharsets.UTF_8));

            long total = System.nanoTime() - start;
            LOG.infof("Analysis finished in %s",
                    total < TimeUnit.MINUTES.toNanos(1) ? TimeUnit.NANOSECONDS.toSeconds(total) + " s"
                            : TimeUnit.NANOSECONDS.toMinutes(total) + "min");

        } catch (IOException | GitAPIException e) {
            throw new IllegalStateException(e);
        }
    }

    private Result analyze(Path workDir, Git git, Ref tag) throws RefAlreadyExistsException, RefNotFoundException,
            InvalidRefNameException, CheckoutConflictException, GitAPIException, IOException {

        Result result = new Result();
        result.setTag(tag);

        // Checkout the tag
        long checkoutStart = System.nanoTime();
        git.checkout().setName(tag.getName()).call();
        LOG.infof("Checked out %s in %s ms", tag.getName(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - checkoutStart));

        long analyzeStart = System.nanoTime();
        List<Path> javaSources;
        AtomicInteger foundFiles = new AtomicInteger();
        LongAdder javaTypes = new LongAdder();
        List<String> buildItems = new ArrayList<>();
        List<String> configItems = new ArrayList<>();

        // We need to consider both old and new Build items API packages
        String oldPackage = "org.jboss.builder.item.";
        String newPackage = "io.quarkus.builder.item.";
        String simpleBuildItem = "SimpleBuildItem";
        String multiBuildItem = "MultiBuildItem";
        String emptyBuildItem = "EmptyBuildItem";
        Set<String> oldNames = new HashSet<>();
        oldNames.add(oldPackage + simpleBuildItem);
        oldNames.add(oldPackage + multiBuildItem);
        Set<String> newNames = new HashSet<>();
        newNames.add(newPackage + simpleBuildItem);
        newNames.add(newPackage + multiBuildItem);
        newNames.add(newPackage + emptyBuildItem);

        javaSources = Files
                .find(workDir, Integer.MAX_VALUE, (path, attrs) -> {
                    if (attrs.isRegularFile()) {
                        foundFiles.incrementAndGet();
                        String pathStr = path.toString();
                        return pathStr.endsWith(".java")
                                && !pathStr.contains("create-extension-pom")
                                && !pathStr.contains("create-extension-templates")
                                && !pathStr.contains("maven-archetype/src/main/resources/archetype-resources")
                                && !pathStr.endsWith(".tpl.qute.java");
                    }
                    return false;
                })
                .collect(Collectors.toList());

        // Parse and analyze java sources
        for (Path javaSource : javaSources) {
            try {
                CompilationUnit unit = StaticJavaParser.parse(javaSource.toFile());

                Map<String, String> imports = new HashMap<>();
                // Foo -> org.acme.Foo
                unit.getImports().stream().forEach(i -> imports.put(i.getName().getIdentifier(), i.getNameAsString()));

                // Java types
                javaTypes.add(unit.findAll(TypeDeclaration.class).stream().count());

                // Collect build items - a build item must be final and extend one of the abstract classes
                unit.findAll(ClassOrInterfaceDeclaration.class).stream()
                        .filter(c -> {
                            return c.isFinal() && c.getExtendedTypes().stream().anyMatch(e -> {
                                String fqcn = null;
                                if (e.getScope().isEmpty()) {
                                    fqcn = imports.get(e.getNameAsString());
                                    if (fqcn == null) {
                                        LOG.debugf("No scope and no import matches: " + e.getNameAsString());
                                    }
                                } else {
                                    fqcn = getFullyQualifiedName(e);
                                }
                                return fqcn != null
                                        && (oldNames.contains(fqcn) || newNames.contains(fqcn));
                            });
                        })
                        .filter(not(ClassOrInterfaceDeclaration::isLocalClassDeclaration))
                        .forEach(c -> buildItems.add(c.getFullyQualifiedName().get()));

                // Collect config items
                String oldConfigItemName = "org.jboss.shamrock.runtime.annotations.ConfigItem";
                unit.findAll(FieldDeclaration.class).stream()
                        .filter(f -> !f.isPrivate())
                        .filter(f -> {
                            NodeList<AnnotationExpr> annotations = f.getAnnotations();
                            if (annotations.isEmpty()) {
                                return false;
                            }
                            for (AnnotationExpr annotation : annotations) {
                                String name = annotation.getNameAsString();
                                if (name.equals(oldConfigItemName) ||
                                        name.equals(ConfigItem.class.getName())) {
                                    return true;
                                }
                                if (name.equals("ConfigItem")) {
                                    // Check imports
                                    String importName = imports.get("ConfigItem");
                                    return oldConfigItemName.equals(importName)
                                            || ConfigItem.class.getName().equals(importName);
                                }
                            }
                            return false;
                        })
                        .forEach(f -> {
                            if (!f.getVariables().isEmpty()) {
                                // We only take the first variable... not 100% correct but should work in most cases
                                VariableDeclarator vd = f.getVariables().get(0);

                                configItems.add(javaSource.getFileName() + "_" + vd.getNameAsString());
                            }
                        });

            } catch (ParseProblemException e) {
                // Quarkus contains a lot of java source templates
                LOG.warnf("Unable to parse: " + javaSource);
            }
        }

        result.setBuildItems(buildItems.size());
        result.setConfigItems(configItems.size());
        result.setJavaSourceFiles(javaSources.size());
        result.setJavaTypes(javaTypes.sum());

        LOG.infof(
                "%s analyzed in %s s:\n\t- %s files\n\t- %s java source files\n\t- %s java types\n\t- %s build items\n\t- %s config items",
                result.getTagName(),
                TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - analyzeStart),
                foundFiles.get(),
                javaSources.size(),
                javaTypes.sum(),
                buildItems.size(),
                configItems.size());

        return result;
    }

    private static String getFullyQualifiedName(ClassOrInterfaceType type) {
        StringBuilder name = new StringBuilder();
        if (type.getScope().isPresent()) {
            name.append(getFullyQualifiedName(type.getScope().get()));
            name.append(".");
        }
        name.append(type.getNameAsString());
        return name.toString();
    }
}
