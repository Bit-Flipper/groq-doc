package dev.bitflippers.groqdoc;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static dev.bitflippers.groqdoc.Groq.COMPLETIONS_URL;
import static org.openrewrite.java.Assertions.java;

@WireMockTest
class GroqDocScanningRecipeTest implements RewriteTest {

    @Language("java")
    private static final String UNDOCUMENTED_INTERFACE =
            """
            package dev.bitflippers.example;
            
            public interface Greetings {
                String helloWorld();
                
                String goodbyeWorld();
                
                String helloTo(String name);
                
                String goodbyeTo(String name);
            }
                                            
            """;

    @Language("java")
    private static final String INTERFACE_WITH_JAVADOC =
            """
            package dev.bitflippers.example;
            
            public interface Greetings {
                /**
                 * @return a generic greeting to the world
                 */
                String helloWorld();
                
                /**
                 * @return a generic farewell to the world
                 */
                String goodbyeWorld();
                
                /**
                 * @return a personalised greeting to the name supplied
                 */
                String helloTo(String name);
                
                /**
                 * @return a personalised farewell to the name supplied
                 */
                String goodbyeTo(String name);
            }
                                            
            """;

    @Language("java")
    private static final String GREETINGS_CLASS = """
        package dev.bitflippers.example;
        
        public class GreetingsImpl implements Greetings {
                @Override
                public String helloWorld() {
                    return "Hello, World!";
                }
                
                @Override
                public String goodbyeWorld() {
                    return "Goodbye, World!";
                }
                
                @Override
                public String helloTo(String name) {
                    return "Hello, " + name + "!";
                }
                
                @Override
                public String goodbyeTo(String name) {
                    return "Goodbye, " + name + "!";
                }
            }
            """;

    private static final String ESCAPED_INTERFACE_WITH_JAVADOC =
            "package dev.bitflippers.example;\\n\\n" +
                    "public interface Greetings {\\n" +
                    "    /**\\n" +
                    "     * @return a generic greeting to the world\\n" +
                    "     */\\n" +
                    "    String helloWorld();\\n" +
                    "\\n" +
                    "    /**\\n" +
                    "     * @return a generic farewell to the world\\n" +
                    "     */\\n" +
                    "    String goodbyeWorld();\\n" +
                    "\\n" +
                    "    /**\\n" +
                    "     * @return a personalised greeting to the name supplied\\n" +
                    "     */\\n" +
                    "    String helloTo(String name);\\n" +
                    "\\n" +
                    "    /**\\n" +
                    "     * @return a personalised farewell to the name supplied\\n" +
                    "     */\\n" +
                    "    String goodbyeTo(String name);\\n" +
                    "}\\n" +
                    "\\n";

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new GroqDocScanningRecipe());
    }

    @BeforeEach
    public void setup(WireMockRuntimeInfo wireMockInfo) {
        Groq.setBaseUrlGlobally(wireMockInfo.getHttpBaseUrl());
        WireMock.stubFor(post(COMPLETIONS_URL)
                .willReturn(ok("{\n" +
                        "    \"id\": \"fdefed5d-d7a9-936f-868a-f8020d85da83\",\n" +
                        "    \"object\": \"chat.completion\",\n" +
                        "    \"created\": 1710411049,\n" +
                        "    \"model\": \"mixtral-8x7b-32768\",\n" +
                        "    \"choices\": [\n" +
                        "        {\n" +
                        "            \"index\": 0,\n" +
                        "            \"message\": {\n" +
                        "                \"role\": \"assistant\",\n" +
                        "                \"content\": \"" + ESCAPED_INTERFACE_WITH_JAVADOC + "\\n\"" +
                        "            },\n" +
                        "            \"logprobs\": null,\n" +
                        "            \"finish_reason\": \"stop\"\n" +
                        "        }\n" +
                        "    ],\n" +
                        "    \"usage\": {\n" +
                        "        \"prompt_tokens\": 125,\n" +
                        "        \"prompt_time\": 0.029,\n" +
                        "        \"completion_tokens\": 243,\n" +
                        "        \"completion_time\": 0.429,\n" +
                        "        \"total_tokens\": 368,\n" +
                        "        \"total_time\": 0.458\n" +
                        "    },\n" +
                        "    \"system_fingerprint\": null\n" +
                        "}")));
    }

    @Test
    public void addJavaDocToInterface() {
        rewriteRun(
                java(UNDOCUMENTED_INTERFACE, INTERFACE_WITH_JAVADOC)
        );
    }

    @Test
    public void addJavaDocToInterfaceWithExtraClassContext() {
        rewriteRun(
                java(UNDOCUMENTED_INTERFACE, INTERFACE_WITH_JAVADOC),
                java(GREETINGS_CLASS)
        );
    }
}