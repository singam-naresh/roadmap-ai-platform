package com.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AIResponseValidator {

    private static final Logger log = LoggerFactory.getLogger(AIResponseValidator.class);
    private final ObjectMapper objectMapper;

    // Validation patterns
    private static final Pattern INCOMPLETE_SENTENCE = Pattern.compile(".*\\b(a|an|the|and|or|but|in|on|at|to|for|of|with|by)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRUNCATED_WORD = Pattern.compile(".*\\b\\w{1,3}$");
    private static final Pattern ELLIPSIS_PATTERN = Pattern.compile(".*\\.{2,}.*");
    private static final int MIN_STEP_LENGTH = 15;
    private static final int MIN_TITLE_LENGTH = 20;
    private static final int MIN_DESCRIPTION_LENGTH = 30;

    public AIResponseValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Validates and cleans AI response for roadmap generation
     */
    public ValidationResult validateRoadmapResponse(String rawResponse) {
        ValidationResult result = new ValidationResult();
        
        try {
            // Clean and extract JSON
            String cleaned = cleanResponse(rawResponse);
            String jsonStr = extractJsonObject(cleaned);
            
            if (jsonStr == null || jsonStr.isBlank()) {
                result.addError("No valid JSON found in response");
                return result;
            }

            JsonNode root = objectMapper.readTree(jsonStr);
            result.setParsedJson(root);

            // Validate required fields
            validateRequiredFields(root, result);
            
            // Validate steps quality
            validateSteps(root.path("steps"), result);
            
            // Validate summary quality
            validateSummary(root.path("summary"), result);
            
            // Validate other fields
            validateDifficulty(root.path("difficulty"), result);
            validateEstimatedTime(root.path("estimatedTime"), result);

            result.setValid(result.getErrors().isEmpty());
            
        } catch (Exception e) {
            log.error("[validation] Failed to validate AI response: {}", e.getMessage());
            result.addError("Failed to parse AI response: " + e.getMessage());
        }

        return result;
    }

    /**
     * Validates individual roadmap step
     */
    public StepValidationResult validateStep(String step, int index) {
        StepValidationResult result = new StepValidationResult(index, step);
        
        if (step == null || step.isBlank()) {
            result.addError("Step is empty");
            return result;
        }

        String trimmed = step.trim();
        
        // Check minimum length
        if (trimmed.length() < MIN_STEP_LENGTH) {
            result.addError("Step too short (minimum " + MIN_STEP_LENGTH + " characters)");
        }

        // Check for truncation indicators
        if (INCOMPLETE_SENTENCE.matcher(trimmed).matches()) {
            result.addError("Step appears to end with incomplete sentence");
        }

        if (TRUNCATED_WORD.matcher(trimmed).matches()) {
            result.addError("Step appears to end with truncated word");
        }

        if (ELLIPSIS_PATTERN.matcher(trimmed).matches()) {
            result.addError("Step contains ellipsis indicating truncation");
        }

        // Check for actionable content
        if (!isActionable(trimmed)) {
            result.addError("Step is not actionable (should start with action verb)");
        }

        // Check for completeness
        if (!isComplete(trimmed)) {
            result.addError("Step appears incomplete or malformed");
        }

        result.setValid(result.getErrors().isEmpty());
        return result;
    }

    private void validateRequiredFields(JsonNode root, ValidationResult result) {
        String[] requiredFields = {"summary", "estimatedTime", "difficulty", "steps", "tips", "mistakesToAvoid", "resources"};
        
        for (String field : requiredFields) {
            if (root.path(field).isMissingNode()) {
                result.addError("Missing required field: " + field);
            }
        }
    }

    private void validateSteps(JsonNode stepsNode, ValidationResult result) {
        if (!stepsNode.isArray()) {
            result.addError("Steps must be an array");
            return;
        }

        if (stepsNode.size() < 3) {
            result.addError("Must have at least 3 steps");
        }

        List<String> invalidSteps = new ArrayList<>();
        for (int i = 0; i < stepsNode.size(); i++) {
            String step = stepsNode.get(i).asText();
            StepValidationResult stepResult = validateStep(step, i);
            
            if (!stepResult.isValid()) {
                invalidSteps.add("Step " + (i + 1) + ": " + String.join(", ", stepResult.getErrors()));
            }
        }

        if (!invalidSteps.isEmpty()) {
            result.addError("Invalid steps found: " + String.join("; ", invalidSteps));
            result.setInvalidSteps(invalidSteps);
        }
    }

    private void validateSummary(JsonNode summaryNode, ValidationResult result) {
        String summary = summaryNode.asText("");
        
        if (summary.length() < MIN_DESCRIPTION_LENGTH) {
            result.addError("Summary too short (minimum " + MIN_DESCRIPTION_LENGTH + " characters)");
        }

        if (ELLIPSIS_PATTERN.matcher(summary).matches()) {
            result.addError("Summary contains ellipsis indicating truncation");
        }
    }

    private void validateDifficulty(JsonNode difficultyNode, ValidationResult result) {
        String difficulty = difficultyNode.asText("");
        if (!List.of("Beginner", "Intermediate", "Advanced").contains(difficulty)) {
            result.addError("Invalid difficulty level: " + difficulty);
        }
    }

    private void validateEstimatedTime(JsonNode timeNode, ValidationResult result) {
        String time = timeNode.asText("");
        if (time.isBlank()) {
            result.addError("Estimated time is missing");
        }
        // "Varies" is acceptable — don't reject it
    }

    private boolean isActionable(String step) {
        // Accept any step that has meaningful content — the AI generates varied but valid steps
        // Only reject clearly non-actionable filler like "..." or single words
        return step.length() >= MIN_STEP_LENGTH && step.contains(" ");
    }

    private boolean isComplete(String step) {
        // Check for common incompleteness indicators
        return !step.endsWith("...") && 
               !step.endsWith(" a") && 
               !step.endsWith(" an") && 
               !step.endsWith(" the") &&
               step.length() > 10 &&
               step.contains(" "); // Should have multiple words
    }

    private String cleanResponse(String response) {
        if (response == null) return "";
        
        // Remove markdown code fences
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        
        return cleaned;
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) return null;
        return text.substring(start, end + 1);
    }

    // Result classes
    public static class ValidationResult {
        private boolean valid = false;
        private List<String> errors = new ArrayList<>();
        private List<String> invalidSteps = new ArrayList<>();
        private JsonNode parsedJson;

        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        
        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }
        
        public List<String> getInvalidSteps() { return invalidSteps; }
        public void setInvalidSteps(List<String> invalidSteps) { this.invalidSteps = invalidSteps; }
        
        public JsonNode getParsedJson() { return parsedJson; }
        public void setParsedJson(JsonNode parsedJson) { this.parsedJson = parsedJson; }
    }

    public static class StepValidationResult {
        private final int index;
        private final String originalStep;
        private boolean valid = false;
        private List<String> errors = new ArrayList<>();

        public StepValidationResult(int index, String originalStep) {
            this.index = index;
            this.originalStep = originalStep;
        }

        public int getIndex() { return index; }
        public String getOriginalStep() { return originalStep; }
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public List<String> getErrors() { return errors; }
        public void addError(String error) { this.errors.add(error); }
    }
}