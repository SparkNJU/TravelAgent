package org.example.backend.dto;

import java.util.List;

public class TravelPlanResponse {
    private Long planId;
    private String title;
    private String destination;
    private Integer days;
    private String itinerary; // AI generated detailed plan
    private Integer estimatedBudget;
    private Double aiConfidenceScore;
    private List<String> highlights;

    public TravelPlanResponse(Long planId, String title, String destination, Integer days,
            String itinerary, Integer estimatedBudget, Double aiConfidenceScore,
            List<String> highlights) {
        this.planId = planId;
        this.title = title;
        this.destination = destination;
        this.days = days;
        this.itinerary = itinerary;
        this.estimatedBudget = estimatedBudget;
        this.aiConfidenceScore = aiConfidenceScore;
        this.highlights = highlights;
    }

    // Getters
    public Long getPlanId() {
        return planId;
    }

    public String getTitle() {
        return title;
    }

    public String getDestination() {
        return destination;
    }

    public Integer getDays() {
        return days;
    }

    public String getItinerary() {
        return itinerary;
    }

    public Integer getEstimatedBudget() {
        return estimatedBudget;
    }

    public Double getAiConfidenceScore() {
        return aiConfidenceScore;
    }

    public List<String> getHighlights() {
        return highlights;
    }
}
