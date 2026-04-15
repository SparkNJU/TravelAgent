package org.example.backend.dto;

public class DestinationResponse {
    private Long id;
    private String name;
    private String description;
    private Double rating;
    private Integer reviewCount;
    private String imageUrl;

    public DestinationResponse(Long id, String name, String description, Double rating, Integer reviewCount,
            String imageUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.imageUrl = imageUrl;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Double getRating() {
        return rating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
