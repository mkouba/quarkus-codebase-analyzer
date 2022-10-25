package com.github.mkouba;

import static java.util.function.Predicate.not;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static Set<String> oldBuildItemNames = Set.of("org.jboss.builder.item.SimpleBuildItem",
            "org.jboss.builder.item.MultiBuildItem");
    private static Set<String> newBuildItemNames = Set.of("io.quarkus.builder.item.SimpleBuildItem",
            "io.quarkus.builder.item.MultiBuildItem", "io.quarkus.builder.item.EmptyBuildItem");

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

    @Option(names = { "-d",
            "--dump" }, description = "Dump the java sources found")
    boolean dump = false;

    @Option(names = { "-t", "--threads" }, description = "The number of threads used to analyze the tags")
    int threads = Runtime.getRuntime().availableProcessors();

    @Override
    public void run() {
        long start = System.nanoTime();
        Path workDir = workDirPath != null ? new File(workDirPath).toPath() : Paths.get("work");
        Path repoDir = workDir.resolve(repository);
        Path reportsDir = workDir.resolve("reports");
        boolean workDirExists = Files.exists(workDir);

        LOG.infof("Working directory: %s", workDirExists ? workDir.toAbsolutePath() : workDir);

        List<Path> threadRepoDirs = new ArrayList<>(threads);

        if (clear || !workDirExists) {
            if (workDirExists) {
                try {
                    Files.walk(workDir)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to clear the working dir: " + workDir, e);
                }
            }
        }
        String remoteRepoName = "https://github.com/" + organization + "/" + repository + ".git";

        // We need a repo per each thread
        Path zeroThreadRepoDir = repoDir.resolve("_0");
        if (!Files.exists(zeroThreadRepoDir)) {
            LOG.infof("Clone %s into %s", remoteRepoName, zeroThreadRepoDir);
            CloneCommand clone = Git.cloneRepository()
                    .setURI(remoteRepoName)
                    .setDirectory(zeroThreadRepoDir.toFile());
            long cloneStart = System.nanoTime();
            try {
                clone.call();
            } catch (GitAPIException e) {
                throw new IllegalStateException("Unable to clone the repo in: " + zeroThreadRepoDir, e);
            }
            LOG.infof("Cloned into %s in %s s", repoDir.toAbsolutePath(),
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - cloneStart));
        }
        threadRepoDirs.add(zeroThreadRepoDir);

        for (int i = 1; i < threads; i++) {
            Path threadRepoDir = repoDir.resolve("_" + i);
            threadRepoDirs.add(threadRepoDir);
            if (!Files.exists(threadRepoDir)) {
                LOG.infof("Copy clone from %s into %s", threadRepoDirs.get(0), threadRepoDir);
                try (Stream<Path> files = Files.walk(zeroThreadRepoDir)) {
                    files.forEach(source -> {
                        if (!source.equals(zeroThreadRepoDir)) {
                            Path dest = threadRepoDir.resolve(zeroThreadRepoDir.relativize(source));
                            try {
                                Files.createDirectories(dest);
                                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING,
                                        StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                            } catch (IOException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                    });
                } catch (IOException e1) {
                    throw new IllegalStateException(e1);
                }
            }
        }

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        // First find the tags
        List<Ref> foundTags;
        try {
            foundTags = Git.open(threadRepoDirs.get(0).toFile()).tagList().call();
        } catch (GitAPIException | IOException e) {
            throw new IllegalStateException("Unable to get the tags from the repo: " + threadRepoDirs.get(0), e);
        }
        LOG.infof("Found %s tags in the repository", foundTags.size());
        List<String> tags = this.tags != null ? Arrays.asList(this.tags.split(","))
                : foundTags.stream().map(Ref::getName).map(Repository::shortenRefName).collect(Collectors.toList());
        LOG.infof("Going to analyze %s tags: %s", tags.size(), tags);

        List<Result> results = new CopyOnWriteArrayList<>();
        BlockingQueue<String> tagsQueue = new ArrayBlockingQueue<>(tags.size(), false, tags);

        for (int i = 0; i < threads; i++) {
            Path threadRepoDir = threadRepoDirs.get(i);
            executor.execute(new Runnable() {

                @Override
                public void run() {
                    LOG.infof("Started thread worker for: %s", threadRepoDir);
                    try {
                        Git git = Git.open(threadRepoDir.toFile());
                        String tag;
                        while ((tag = tagsQueue.poll()) != null) {
                            analyzeTag(tag, foundTags, results, threadRepoDir, git);
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            });
        }

        // Wait for the results...
        // TODO add timeout
        while (results.size() != tags.size()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        // Sort the results
        Collections.sort(results);

        // Render reports
        try {
            if (!Files.exists(reportsDir)) {
                Files.createDirectories(reportsDir);
            }
            Files.write(reportsDir.resolve("report.html"),
                    Templates.report(results).render().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        long total = System.nanoTime() - start;
        LOG.infof("Analysis finished in %s",
                total < TimeUnit.MINUTES.toNanos(1) ? TimeUnit.NANOSECONDS.toSeconds(total) + " s"
                        : TimeUnit.NANOSECONDS.toMinutes(total) + " min");

        executor.shutdownNow();
    }

    private void analyzeTag(String tag, List<Ref> foundTags, List<Result> results, Path threadRepoDir, Git git)
            throws RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException, CheckoutConflictException,
            GitAPIException, IOException {
        Optional<Ref> foundTag = foundTags.stream()
                .filter(ref -> Repository.shortenRefName(ref.getName()).equals(tag))
                .findFirst();
        if (foundTag.isEmpty()) {
            throw new IllegalStateException("Tag not found: " + tag);
        }
        results.add(analyze(threadRepoDir, git, foundTag.get()));
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
        List<Path> testJavaSources = new ArrayList<>();
        AtomicInteger foundFiles = new AtomicInteger();
        LongAdder javaTypes = new LongAdder();
        LongAdder testJavaTypes = new LongAdder();
        List<String> buildItems = new ArrayList<>();
        List<String> configItems = new ArrayList<>();

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
                String fileName = javaSource.toString();
                boolean isTest = fileName.contains("test/java") || fileName.contains("test\\java")
                        || fileName.contains("integration-tests");
                if (isTest) {
                    testJavaSources.add(javaSource);
                }

                CompilationUnit unit = StaticJavaParser.parse(javaSource.toFile());

                Map<String, String> imports = new HashMap<>();
                // Foo -> org.acme.Foo
                unit.getImports().stream().forEach(i -> imports.put(i.getName().getIdentifier(), i.getNameAsString()));

                // Java types
                long typesFound = unit.findAll(TypeDeclaration.class).stream().count();
                javaTypes.add(typesFound);
                if (isTest) {
                    testJavaTypes.add(typesFound);
                }

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
                                        && (oldBuildItemNames.contains(fqcn) || newBuildItemNames.contains(fqcn));
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
        result.setTestSourceFiles(testJavaSources.size());
        result.setTestTypes(testJavaTypes.sum());

        LOG.infof(
                "%s analyzed in %s s:\n\t- %s files\n\t- %s java source files\n\t- %s java types\n\t- %s build items\n\t- %s config items",
                result.getTagName(),
                TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - analyzeStart),
                foundFiles.get(),
                javaSources.size(),
                javaTypes.sum(),
                buildItems.size(),
                configItems.size());

        if (dump) {
            LOG.infof("Java sources found:\n- %s", javaSources.stream().map(p -> {
                String str = p.toString();
                if (testJavaSources.contains(p)) {
                    str += " [TEST]";
                }
                return str;
            }).collect(Collectors.joining("\n- ")));
        }

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
