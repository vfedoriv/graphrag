package io.github.vfedoriv.graphrag.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DocumentationAlignmentTest {

    private static final Path PORTAL_ROOT = Path.of("src/site/markdown");
    private static final Path SITE_DESCRIPTOR = Path.of("src/site/site.xml");
    private static final Pattern PARENT_VERSION = Pattern.compile(
        "<parent>\\s*<groupId>org\\.springframework\\.boot</groupId>\\s*<artifactId>spring-boot-starter-parent</artifactId>\\s*<version>([^<]+)</version>",
        Pattern.DOTALL
    );
    private static final Pattern POM_PROPERTY = Pattern.compile("<([^>]+)>\\s*([^<]+)\\s*</\\1>");
    private static final Pattern MENU = Pattern.compile("<menu\\s+name=\"([^\"]+)\"");
    private static final Pattern NAVIGATION_TARGET = Pattern.compile("<item\\s+name=\"[^\"]+\"\\s+href=\"([^\"]+\\.html)\"");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("!?\\[[^]]*]\\(([^)]+)\\)");
    private static final Pattern EXTERNAL_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");

    @Test
    void sharedStackFactsMatchPomAndCanonicalPortal() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        String readme = Files.readString(Path.of("README.md"));
        String agents = Files.readString(Path.of("AGENTS.md"));
        String claude = Files.readString(Path.of("CLAUDE.md"));
        String portal = Files.readString(PORTAL_ROOT.resolve("index.md"));

        Matcher bootMatcher = PARENT_VERSION.matcher(pom);
        assertThat(bootMatcher.find()).as("Spring Boot parent version in pom.xml").isTrue();
        String springBootVersion = bootMatcher.group(1);
        String springAiVersion = pomProperty(pom, "spring-ai.version");
        String langchainVersion = pomProperty(pom, "langchain4j.version");

        for (String guidance : List.of(readme, agents, claude, portal)) {
            assertThat(guidance).contains(
                "Spring Boot " + springBootVersion,
                "Spring AI " + springAiVersion,
                "LangChain4j " + langchainVersion
            );
        }
    }

    @Test
    void canonicalConfigurationGuideMatchesApplicationDefaults() throws IOException {
        Properties properties = loadProperties(Path.of("src/main/resources/application.properties"));
        String guide = Files.readString(PORTAL_ROOT.resolve("operations/configuration.md"));

        assertThat(guide).contains(
            "app.chunking.strategy=" + properties.getProperty("app.chunking.strategy"),
            "app.chunking.target-tokens=" + properties.getProperty("app.chunking.max-tokens"),
            "app.query.max-rows=" + properties.getProperty("app.query.max-rows"),
            "app.advanced-search.default-evidence=" + properties.getProperty("app.advanced-search.default-evidence"),
            "app.extraction.max-entities-per-chunk=" + properties.getProperty("app.extraction.max-entities-per-chunk"),
            "`mutable=true`",
            "`pending-restart`",
            "`logging.level.root`",
            "Profile-managed",
            "deployment-managed"
        );
    }

    @Test
    void advancedSearchDocumentationUsesCurrentAsyncContract() throws IOException {
        String guide = Files.readString(PORTAL_ROOT.resolve("workflows/advanced-search.md"));

        assertThat(guide)
            .contains(
                "/queries/advanced-search-runs/readiness",
                "\"maximumEvidence\":10",
                "queryPreview",
                "QUEUED",
                "PARTIAL",
                "CANCELLED",
                "INTERRUPTED"
            )
            .doesNotContain("/queries/hybrid-search", "Hybrid Search");
    }

    @Test
    void portalNavigationCoversEveryMarkdownPageAndRequiredGroups() throws IOException {
        String site = Files.readString(SITE_DESCRIPTOR);
        Set<String> navigationTargets = matches(site, NAVIGATION_TARGET);
        navigationTargets.removeIf(target -> EXTERNAL_SCHEME.matcher(target).find());
        Set<String> navigationGroups = matches(site, MENU);

        assertThat(navigationGroups).containsExactly(
            "Getting Started",
            "Concepts",
            "Workflows",
            "Operations",
            "Reference",
            "Contributing"
        );

        Set<String> portalTargets = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(PORTAL_ROOT)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".md"))
                .map(PORTAL_ROOT::relativize)
                .map(Path::toString)
                .map(path -> path.substring(0, path.length() - 3) + ".html")
                .map(path -> path.replace('\\', '/'))
                .forEach(portalTargets::add);
        }

        assertThat(navigationTargets).containsExactlyInAnyOrderElementsOf(portalTargets);
        for (String target : navigationTargets) {
            Path markdown = PORTAL_ROOT.resolve(target.substring(0, target.length() - 5) + ".md");
            assertThat(markdown)
                .as("navigation target %s", target)
                .isRegularFile();
        }
    }

    @Test
    void relativeMarkdownLinksAndImageTargetsExist() throws IOException {
        List<Path> sources = new ArrayList<>();
        sources.add(Path.of("README.md"));
        try (Stream<Path> paths = Files.walk(PORTAL_ROOT)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".md"))
                .forEach(sources::add);
        }
        try (Stream<Path> paths = Files.walk(Path.of("docs"))) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".md"))
                .forEach(sources::add);
        }

        for (Path source : sources) {
            Matcher matcher = MARKDOWN_LINK.matcher(Files.readString(source));
            while (matcher.find()) {
                String rawTarget = matcher.group(1).trim();
                if (rawTarget.isEmpty() || rawTarget.startsWith("#") || EXTERNAL_SCHEME.matcher(rawTarget).find()) {
                    continue;
                }
                String target = stripFragmentAndQuery(rawTarget);
                Path resolved = source.getParent() == null
                    ? Path.of(target).normalize()
                    : source.getParent().resolve(target).normalize();
                assertThat(resolved)
                    .as("%s references local target %s", source, rawTarget)
                    .exists();
            }
        }
    }

    @Test
    void reciprocalRepositoryLinksAndMermaidResourcesArePinned() throws IOException {
        String readme = Files.readString(Path.of("README.md"));
        String agents = Files.readString(Path.of("AGENTS.md"));
        String claude = Files.readString(Path.of("CLAUDE.md"));
        String maintenance = Files.readString(PORTAL_ROOT.resolve("contributing/documentation.md"));
        String chunking = Files.readString(PORTAL_ROOT.resolve("workflows/chunking-reprocessing.md"));
        String advanced = Files.readString(PORTAL_ROOT.resolve("workflows/advanced-search.md"));
        String site = Files.readString(SITE_DESCRIPTOR);
        String mermaid = Files.readString(Path.of("src/site/resources/js/mermaid-init.js"));
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(readme).contains(
            "https://github.com/vfedoriv/graphrag/blob/main/src/site/markdown/index.md",
            "https://github.com/vfedoriv/graphrag-ui"
        );
        assertThat(agents).contains("graphrag/blob/main/src/site/markdown/index.md", "graphrag-ui/tree/main");
        assertThat(claude).contains("graphrag/blob/main/src/site/markdown/index.md", "graphrag-ui/tree/main");
        assertThat(maintenance).contains("graphrag-ui/tree/main/openspec/changes/add-multipage-documentation-portal");
        assertThat(chunking).contains("graphrag-ui/blob/main/docs/chunking/README.md");
        assertThat(advanced).contains("graphrag-ui/blob/main/docs/advanced-search/README.md");
        assertThat(site).contains(
            "https://github.com/vfedoriv/graphrag",
            "https://github.com/vfedoriv/graphrag-ui",
            "js/mermaid-init.js"
        );
        assertThat(mermaid).contains("mermaid@11.15.0", "pre > code.language-mermaid");
        assertThat(pom).contains(
            "<artifactId>maven-site-plugin</artifactId>",
            "<version>3.22.0</version>"
        );
        assertThat(site).contains(
            "<artifactId>maven-fluido-skin</artifactId>",
            "<version>2.1.0</version>"
        );
    }

    private Set<String> matches(String input, Pattern pattern) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private String pomProperty(String pom, String name) {
        Matcher matcher = POM_PROPERTY.matcher(pom);
        while (matcher.find()) {
            if (matcher.group(1).equals(name)) {
                return matcher.group(2).trim();
            }
        }
        throw new AssertionError("Missing Maven property: " + name);
    }

    private Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return properties;
    }

    private String stripFragmentAndQuery(String target) {
        int fragment = target.indexOf('#');
        int query = target.indexOf('?');
        int end = target.length();
        if (fragment >= 0) {
            end = Math.min(end, fragment);
        }
        if (query >= 0) {
            end = Math.min(end, query);
        }
        return target.substring(0, end);
    }
}
