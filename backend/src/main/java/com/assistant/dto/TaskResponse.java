package com.assistant.dto;

import java.time.LocalDateTime;
import java.util.List;

public class TaskResponse {

    private Long id;
    private String userInput;

    // ── Intent routing ────────────────────────────────────────────────────────
    // Tells the frontend which renderer to use.
    // Values: ROADMAP | CHAT | CODING | ANALYSIS | PRODUCTIVITY | STARTUP | LEARNING
    private String intentType;

    // ── Conversation threading ────────────────────────────────────────────────
    // Present when this response belongs to a multi-turn conversation.
    private Long conversationId;

    // ── Shared fields (used by all intent types) ──────────────────────────────
    private String summary;
    private String category;
    private String skillLevel;
    private String mode;
    private String aiOutput;
    private LocalDateTime createdAt;

    // ── ROADMAP / LEARNING / PRODUCTIVITY / STARTUP fields ───────────────────
    private String estimatedTime;
    private String difficulty;
    private List<String> prerequisites;
    private List<String> steps;
    private List<String> tips;
    private List<String> mistakesToAvoid;
    private List<String> resources;

    // ── CHAT fields ───────────────────────────────────────────────────────────
    private String message;           // plain conversational response text
    private List<String> suggestions; // optional follow-up suggestions

    // ── CODING fields ─────────────────────────────────────────────────────────
    private String language;
    private String explanation;
    private List<CodeBlock> codeBlocks;
    private List<String> keyPoints;
    private List<String> commonMistakes;
    private List<String> clarificationQuestions; // populated when prompt is too vague

    // ── ANALYSIS fields ───────────────────────────────────────────────────────
    private String verdict;
    private List<AnalysisSection> sections;
    private List<String> pros;
    private List<String> cons;
    private List<String> recommendations;
    private List<String> useCases;

    // ── LEARNING fields ───────────────────────────────────────────────────────
    private String conceptTitle;
    private String conceptExplanation;
    private List<LearningExample> examples;
    private List<String> commonMisconceptions;
    private List<String> practiceExercises;
    private List<String> bestPractices;
    private List<String> nextTopics;

    // ── PRODUCTIVITY fields ───────────────────────────────────────────────────
    private String systemTitle;
    private String overview;
    private List<ScheduleBlock> schedule;
    private List<String> priorities;
    private List<String> habits;
    private List<String> tools;
    private String weeklyReview;

    // =========================================================================
    // Nested types
    // =========================================================================

    public static class CodeBlock {
        private String label;
        private String language;
        private String code;

        public CodeBlock() {}
        public CodeBlock(String label, String language, String code) {
            this.label = label; this.language = language; this.code = code;
        }
        public String getLabel()    { return label; }
        public void setLabel(String v) { this.label = v; }
        public String getLanguage() { return language; }
        public void setLanguage(String v) { this.language = v; }
        public String getCode()     { return code; }
        public void setCode(String v) { this.code = v; }
    }

    public static class AnalysisSection {
        private String title;
        private String content;
        private Integer score;

        public AnalysisSection() {}
        public String getTitle()   { return title; }
        public void setTitle(String v) { this.title = v; }
        public String getContent() { return content; }
        public void setContent(String v) { this.content = v; }
        public Integer getScore()  { return score; }
        public void setScore(Integer v) { this.score = v; }
    }

    public static class LearningExample {
        private String title;
        private String description;
        private String code;

        public LearningExample() {}
        public String getTitle()       { return title; }
        public void setTitle(String v) { this.title = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { this.description = v; }
        public String getCode()        { return code; }
        public void setCode(String v)  { this.code = v; }
    }

    public static class ScheduleBlock {
        private String timeBlock;
        private String activity;
        private String priority;
        private String duration;

        public ScheduleBlock() {}
        public String getTimeBlock()       { return timeBlock; }
        public void setTimeBlock(String v) { this.timeBlock = v; }
        public String getActivity()        { return activity; }
        public void setActivity(String v)  { this.activity = v; }
        public String getPriority()        { return priority; }
        public void setPriority(String v)  { this.priority = v; }
        public String getDuration()        { return duration; }
        public void setDuration(String v)  { this.duration = v; }
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    public TaskResponse() {}

    private TaskResponse(Builder builder) {
        this.id               = builder.id;
        this.userInput        = builder.userInput;
        this.intentType       = builder.intentType;
        this.conversationId   = builder.conversationId;
        this.summary          = builder.summary;
        this.category         = builder.category;
        this.skillLevel       = builder.skillLevel;
        this.mode             = builder.mode;
        this.aiOutput         = builder.aiOutput;
        this.createdAt        = builder.createdAt;
        this.estimatedTime    = builder.estimatedTime;
        this.difficulty       = builder.difficulty;
        this.prerequisites    = builder.prerequisites;
        this.steps            = builder.steps;
        this.tips             = builder.tips;
        this.mistakesToAvoid  = builder.mistakesToAvoid;
        this.resources        = builder.resources;
        this.message          = builder.message;
        this.suggestions      = builder.suggestions;
        this.language         = builder.language;
        this.explanation      = builder.explanation;
        this.codeBlocks       = builder.codeBlocks;
        this.keyPoints        = builder.keyPoints;
        this.commonMistakes   = builder.commonMistakes;
        this.clarificationQuestions = builder.clarificationQuestions;
        this.verdict          = builder.verdict;
        this.sections         = builder.sections;
        this.pros             = builder.pros;
        this.cons             = builder.cons;
        this.recommendations  = builder.recommendations;
        this.useCases         = builder.useCases;
        this.conceptTitle     = builder.conceptTitle;
        this.conceptExplanation = builder.conceptExplanation;
        this.examples         = builder.examples;
        this.commonMisconceptions = builder.commonMisconceptions;
        this.practiceExercises = builder.practiceExercises;
        this.bestPractices    = builder.bestPractices;
        this.nextTopics       = builder.nextTopics;
        this.systemTitle      = builder.systemTitle;
        this.overview         = builder.overview;
        this.schedule         = builder.schedule;
        this.priorities       = builder.priorities;
        this.habits           = builder.habits;
        this.tools            = builder.tools;
        this.weeklyReview     = builder.weeklyReview;
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public Long getId()                              { return id; }
    public void setId(Long v)                        { this.id = v; }
    public String getUserInput()                     { return userInput; }
    public void setUserInput(String v)               { this.userInput = v; }
    public String getIntentType()                    { return intentType; }
    public void setIntentType(String v)              { this.intentType = v; }
    public Long getConversationId()                  { return conversationId; }
    public void setConversationId(Long v)            { this.conversationId = v; }
    public String getSummary()                       { return summary; }
    public void setSummary(String v)                 { this.summary = v; }
    public String getCategory()                      { return category; }
    public void setCategory(String v)                { this.category = v; }
    public String getSkillLevel()                    { return skillLevel; }
    public void setSkillLevel(String v)              { this.skillLevel = v; }
    public String getMode()                          { return mode; }
    public void setMode(String v)                    { this.mode = v; }
    public String getAiOutput()                      { return aiOutput; }
    public void setAiOutput(String v)                { this.aiOutput = v; }
    public LocalDateTime getCreatedAt()              { return createdAt; }
    public void setCreatedAt(LocalDateTime v)        { this.createdAt = v; }
    public String getEstimatedTime()                 { return estimatedTime; }
    public void setEstimatedTime(String v)           { this.estimatedTime = v; }
    public String getDifficulty()                    { return difficulty; }
    public void setDifficulty(String v)              { this.difficulty = v; }
    public List<String> getPrerequisites()           { return prerequisites; }
    public void setPrerequisites(List<String> v)     { this.prerequisites = v; }
    public List<String> getSteps()                   { return steps; }
    public void setSteps(List<String> v)             { this.steps = v; }
    public List<String> getTips()                    { return tips; }
    public void setTips(List<String> v)              { this.tips = v; }
    public List<String> getMistakesToAvoid()         { return mistakesToAvoid; }
    public void setMistakesToAvoid(List<String> v)   { this.mistakesToAvoid = v; }
    public List<String> getResources()               { return resources; }
    public void setResources(List<String> v)         { this.resources = v; }
    public String getMessage()                       { return message; }
    public void setMessage(String v)                 { this.message = v; }
    public List<String> getSuggestions()             { return suggestions; }
    public void setSuggestions(List<String> v)       { this.suggestions = v; }
    public String getLanguage()                      { return language; }
    public void setLanguage(String v)                { this.language = v; }
    public String getExplanation()                   { return explanation; }
    public void setExplanation(String v)             { this.explanation = v; }
    public List<CodeBlock> getCodeBlocks()           { return codeBlocks; }
    public void setCodeBlocks(List<CodeBlock> v)     { this.codeBlocks = v; }
    public List<String> getKeyPoints()               { return keyPoints; }
    public void setKeyPoints(List<String> v)         { this.keyPoints = v; }
    public List<String> getCommonMistakes()          { return commonMistakes; }
    public void setCommonMistakes(List<String> v)    { this.commonMistakes = v; }
    public List<String> getClarificationQuestions()  { return clarificationQuestions; }
    public void setClarificationQuestions(List<String> v) { this.clarificationQuestions = v; }
    public String getVerdict()                       { return verdict; }
    public void setVerdict(String v)                 { this.verdict = v; }
    public List<AnalysisSection> getSections()       { return sections; }
    public void setSections(List<AnalysisSection> v) { this.sections = v; }
    public List<String> getPros()                    { return pros; }
    public void setPros(List<String> v)              { this.pros = v; }
    public List<String> getCons()                    { return cons; }
    public void setCons(List<String> v)              { this.cons = v; }
    public List<String> getRecommendations()         { return recommendations; }
    public void setRecommendations(List<String> v)   { this.recommendations = v; }
    public List<String> getUseCases()                { return useCases; }
    public void setUseCases(List<String> v)          { this.useCases = v; }
    // LEARNING
    public String getConceptTitle()                  { return conceptTitle; }
    public void setConceptTitle(String v)            { this.conceptTitle = v; }
    public String getConceptExplanation()            { return conceptExplanation; }
    public void setConceptExplanation(String v)      { this.conceptExplanation = v; }
    public List<LearningExample> getExamples()       { return examples; }
    public void setExamples(List<LearningExample> v) { this.examples = v; }
    public List<String> getCommonMisconceptions()    { return commonMisconceptions; }
    public void setCommonMisconceptions(List<String> v) { this.commonMisconceptions = v; }
    public List<String> getPracticeExercises()       { return practiceExercises; }
    public void setPracticeExercises(List<String> v) { this.practiceExercises = v; }
    public List<String> getBestPractices()           { return bestPractices; }
    public void setBestPractices(List<String> v)     { this.bestPractices = v; }
    public List<String> getNextTopics()              { return nextTopics; }
    public void setNextTopics(List<String> v)        { this.nextTopics = v; }
    // PRODUCTIVITY
    public String getSystemTitle()                   { return systemTitle; }
    public void setSystemTitle(String v)             { this.systemTitle = v; }
    public String getOverview()                      { return overview; }
    public void setOverview(String v)                { this.overview = v; }
    public List<ScheduleBlock> getSchedule()         { return schedule; }
    public void setSchedule(List<ScheduleBlock> v)   { this.schedule = v; }
    public List<String> getPriorities()              { return priorities; }
    public void setPriorities(List<String> v)        { this.priorities = v; }
    public List<String> getHabits()                  { return habits; }
    public void setHabits(List<String> v)            { this.habits = v; }
    public List<String> getTools()                   { return tools; }
    public void setTools(List<String> v)             { this.tools = v; }
    public String getWeeklyReview()                  { return weeklyReview; }
    public void setWeeklyReview(String v)            { this.weeklyReview = v; }

    // =========================================================================
    // Builder
    // =========================================================================

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long id;
        private String userInput, intentType, summary, category, skillLevel, mode, aiOutput;
        private Long conversationId;
        private LocalDateTime createdAt;
        private String estimatedTime, difficulty;
        private List<String> prerequisites, steps, tips, mistakesToAvoid, resources;
        private String message;
        private List<String> suggestions;
        private String language, explanation;
        private List<CodeBlock> codeBlocks;
        private List<String> keyPoints, commonMistakes, clarificationQuestions;
        private String verdict;
        private List<AnalysisSection> sections;
        private List<String> pros, cons, recommendations, useCases;
        // LEARNING
        private String conceptTitle, conceptExplanation;
        private List<LearningExample> examples;
        private List<String> commonMisconceptions, practiceExercises, bestPractices, nextTopics;
        // PRODUCTIVITY
        private String systemTitle, overview, weeklyReview;
        private List<ScheduleBlock> schedule;
        private List<String> priorities, habits, tools;

        public Builder id(Long v)                          { this.id = v;              return this; }
        public Builder userInput(String v)                 { this.userInput = v;       return this; }
        public Builder intentType(String v)                { this.intentType = v;      return this; }
        public Builder conversationId(Long v)              { this.conversationId = v;  return this; }
        public Builder summary(String v)                   { this.summary = v;         return this; }
        public Builder category(String v)                  { this.category = v;        return this; }
        public Builder skillLevel(String v)                { this.skillLevel = v;      return this; }
        public Builder mode(String v)                      { this.mode = v;            return this; }
        public Builder aiOutput(String v)                  { this.aiOutput = v;        return this; }
        public Builder createdAt(LocalDateTime v)          { this.createdAt = v;       return this; }
        public Builder estimatedTime(String v)             { this.estimatedTime = v;   return this; }
        public Builder difficulty(String v)                { this.difficulty = v;      return this; }
        public Builder prerequisites(List<String> v)       { this.prerequisites = v;   return this; }
        public Builder steps(List<String> v)               { this.steps = v;           return this; }
        public Builder tips(List<String> v)                { this.tips = v;            return this; }
        public Builder mistakesToAvoid(List<String> v)     { this.mistakesToAvoid = v; return this; }
        public Builder resources(List<String> v)           { this.resources = v;       return this; }
        public Builder message(String v)                   { this.message = v;         return this; }
        public Builder suggestions(List<String> v)         { this.suggestions = v;     return this; }
        public Builder language(String v)                  { this.language = v;        return this; }
        public Builder explanation(String v)               { this.explanation = v;     return this; }
        public Builder codeBlocks(List<CodeBlock> v)       { this.codeBlocks = v;      return this; }
        public Builder keyPoints(List<String> v)           { this.keyPoints = v;       return this; }
        public Builder commonMistakes(List<String> v)      { this.commonMistakes = v;  return this; }
        public Builder clarificationQuestions(List<String> v) { this.clarificationQuestions = v; return this; }
        public Builder verdict(String v)                   { this.verdict = v;         return this; }
        public Builder sections(List<AnalysisSection> v)   { this.sections = v;        return this; }
        public Builder pros(List<String> v)                { this.pros = v;            return this; }
        public Builder cons(List<String> v)                { this.cons = v;            return this; }
        public Builder recommendations(List<String> v)     { this.recommendations = v; return this; }
        public Builder useCases(List<String> v)            { this.useCases = v;        return this; }
        // LEARNING
        public Builder conceptTitle(String v)              { this.conceptTitle = v;    return this; }
        public Builder conceptExplanation(String v)        { this.conceptExplanation = v; return this; }
        public Builder examples(List<LearningExample> v)   { this.examples = v;        return this; }
        public Builder commonMisconceptions(List<String> v){ this.commonMisconceptions = v; return this; }
        public Builder practiceExercises(List<String> v)   { this.practiceExercises = v; return this; }
        public Builder bestPractices(List<String> v)       { this.bestPractices = v;   return this; }
        public Builder nextTopics(List<String> v)          { this.nextTopics = v;      return this; }
        // PRODUCTIVITY
        public Builder systemTitle(String v)               { this.systemTitle = v;     return this; }
        public Builder overview(String v)                  { this.overview = v;        return this; }
        public Builder schedule(List<ScheduleBlock> v)     { this.schedule = v;        return this; }
        public Builder priorities(List<String> v)          { this.priorities = v;      return this; }
        public Builder habits(List<String> v)              { this.habits = v;          return this; }
        public Builder tools(List<String> v)               { this.tools = v;           return this; }
        public Builder weeklyReview(String v)              { this.weeklyReview = v;    return this; }

        public TaskResponse build() { return new TaskResponse(this); }
    }
}
