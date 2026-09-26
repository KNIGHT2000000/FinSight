package com.finsight.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/assistant/query.
 */
public record AssistantRequest(

        @NotBlank(message = "Question must not be blank")
        @Size(max = 1000, message = "Question must not exceed 1000 characters")
        String question
) {}
