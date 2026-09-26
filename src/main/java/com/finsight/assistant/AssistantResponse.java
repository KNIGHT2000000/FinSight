package com.finsight.assistant;

import java.util.List;

/**
 * Response body for POST /api/v1/assistant/query.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code answer} — plain-language response from the LLM grounded in retrieved context.
 *   <li>{@code suggestedRule} — nullable. Populated only when the LLM identifies a
 *       plausible new surveillance rule in the retrieved content. Always labelled
 *       "UNREVIEWED — REQUIRES ANALYST SIGN-OFF".
 *   <li>{@code retrievedSources} — filenames of the typology documents used as context.
 *   <li>{@code disclaimer} — governance statement included in every response.
 * </ul>
 */
public class AssistantResponse {

    private final String       answer;
    private final String       suggestedRule;    // nullable
    private final List<String> retrievedSources;
    private final String       disclaimer;

    public AssistantResponse(String answer,
                              String suggestedRule,
                              List<String> retrievedSources,
                              String disclaimer) {
        this.answer           = answer;
        this.suggestedRule    = suggestedRule;
        this.retrievedSources = retrievedSources;
        this.disclaimer       = disclaimer;
    }

    public String       getAnswer()           { return answer; }
    public String       getSuggestedRule()    { return suggestedRule; }
    public List<String> getRetrievedSources() { return retrievedSources; }
    public String       getDisclaimer()       { return disclaimer; }
}
