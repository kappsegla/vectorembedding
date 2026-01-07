package org.example.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.List;

public class LMStudioClient {

    // --- Configuration ---
    // TODO: Externalize these settings.
    private static final String API_URL = "http://localhost:1234/v1/embeddings";
    // This model name should match the one loaded in your LM Studio instance.
    private static final String MODEL_NAME = "text-embedding-embeddinggemma-300m-qat";

    //Vector length on embeddinggemma is 3072
    //Matryoshka Representation Learning.
    //Namnet kommer från de ryska dockorna som ligger i varandra. Modellen tränas att komprimera den viktigaste informationen i de första dimensionerna. De extra dimensionerna (upp till 3072) lägger bara till mer finkorniga detaljer.


    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generates an embedding vector for the given text by calling the LM Studio API.
     *
     * @param text The text to embed.
     * @return A float array representing the embedding vector.
     * @throws IOException if the API call fails.
     */
    public float[] getEmbedding(String text) throws IOException {
        // 1. Create the JSON request body
        var embeddingRequest = new EmbeddingRequest(List.of(text), MODEL_NAME,768);
        String jsonBody = objectMapper.writeValueAsString(embeddingRequest);

        RequestBody requestBody = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .build();

        // 2. Execute the request and get the response
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Unexpected API response code: " + response.code() + " - " + response.message());
            }

            // 3. Parse the JSON response
            String responseBody = response.body().string();
            EmbeddingResponse embeddingResponse = objectMapper.readValue(responseBody, EmbeddingResponse.class);

            // 4. Extract and convert the embedding to a float array
            if (embeddingResponse.data() == null || embeddingResponse.data().isEmpty() || embeddingResponse.data().get(0).embedding() == null) {
                throw new IOException("API response did not contain a valid embedding.");
            }

            List<Double> embeddingAsDoubleList = embeddingResponse.data().get(0).embedding();
            float[] embedding = new float[embeddingAsDoubleList.size()];
            for (int i = 0; i < embeddingAsDoubleList.size(); i++) {
                embedding[i] = embeddingAsDoubleList.get(i).floatValue();
            }
            return embedding;
        }
    }

    // --- DTOs for JSON serialization/deserialization ---

    private record EmbeddingRequest(
            List<String> input,
            String model,
            Integer dimensions
    ) {
    }

    private record EmbeddingResponse(
            @JsonProperty("data") List<EmbeddingData> data,
            @JsonProperty("model") String model,
            @JsonProperty("object") String object,
            @JsonProperty("usage") Usage usage
    ) {
    }

    private record EmbeddingData(
            @JsonProperty("embedding") List<Double> embedding,
            @JsonProperty("index") int index,
            @JsonProperty("object") String object
    ) {
    }

    private record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("total_tokens") int totalTokens
    ) {
    }
}