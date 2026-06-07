package org.example.backend.dto;

import java.util.List;
import org.example.backend.entity.PlanActivity;

public class SavePlanRequest {
    private Long userId;
    private String title;
    private String destination;
    private Integer days;
    private List<PlanActivity> activities;
    private Integer budget;
    private String interests;
    private String travelStyle;
    private List<String> highlights;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public List<PlanActivity> getActivities() {
        return activities;
    }

    public void setActivities(List<PlanActivity> activities) {
        this.activities = activities;
    }

    public Integer getBudget() {
        return budget;
    }

    public void setBudget(Integer budget) {
        this.budget = budget;
    }

    public String getInterests() {
        return interests;
    }

    public void setInterests(String interests) {
        this.interests = interests;
    }

    public String getTravelStyle() {
        return travelStyle;
    }

    public void setTravelStyle(String travelStyle) {
        this.travelStyle = travelStyle;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }
}