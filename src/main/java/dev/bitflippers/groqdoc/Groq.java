package dev.bitflippers.groqdoc;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bitflippers.groqdoc.model.GroqRequest;
import dev.bitflippers.groqdoc.model.GroqResponse;
import dev.bitflippers.groqdoc.model.Message;
import dev.bitflippers.groqdoc.model.Model;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;
import static org.apache.hc.core5.http.HttpHeaders.AUTHORIZATION;

public class Groq {
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static String BASE_URL = "https://api.groq.com";
    public static final String COMPLETIONS_URL = "/openai/v1/chat/completions";
    private static final String GROQ_API_KEY = System.getenv("GROQ_API_KEY");

    static {
        if (GROQ_API_KEY == null) {
            throw new IllegalStateException("GROQ_API_KEY environment variable must be set");
        }
    }

    public static void setBaseUrlGlobally(String baseUrl) {
        BASE_URL = baseUrl;
    }

    public Optional<List<GroqResponse.Choice>> createCompletion(List<Message> messages) {
        return createCompletion(messages.toArray(new Message[]{}));
    }

    public Optional<List<GroqResponse.Choice>> createCompletion(Message... messages) {
        try {
            var payload = mapper.writeValueAsBytes(new GroqRequest(Arrays.asList(messages), Model.MIXTRAL));
            var request = Request.post(BASE_URL + COMPLETIONS_URL)
                    .addHeader(AUTHORIZATION, "Bearer " + GROQ_API_KEY)
                    .bodyByteArray(payload, APPLICATION_JSON);

            var response = request.execute()
                    .handleResponse(new GroqResponseHandler());
            return Optional.of(response.choices());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static class GroqResponseHandler implements HttpClientResponseHandler<GroqResponse> {
        @Override
        public GroqResponse handleResponse(ClassicHttpResponse response) throws IOException {
            if (response.getCode() != 200) {
                throw new IOException("Unexpected status code: " + response.getCode());
            }

            return mapper.readValue(response.getEntity().getContent(), GroqResponse.class);
        }
    }
}
