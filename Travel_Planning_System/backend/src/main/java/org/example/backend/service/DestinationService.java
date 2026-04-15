package org.example.backend.service;

import org.example.backend.dto.DestinationResponse;
import org.example.backend.entity.Destination;
import org.example.backend.repository.DestinationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DestinationService {

    @Autowired
    private DestinationRepository destinationRepository;

    /**
     * Get popular travel destinations (top rated)
     */
    public List<DestinationResponse> getPopularDestinations() {
        List<Destination> destinations = destinationRepository.findAllByOrderByRatingDesc();
        return destinations.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Search destinations by keyword
     */
    public List<DestinationResponse> searchDestinations(String keyword) {
        List<Destination> results = destinationRepository.searchByKeyword(keyword);
        return results.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get destination by ID
     */
    public DestinationResponse getDestinationById(Long id) {
        return destinationRepository.findById(id)
                .map(this::convertToResponse)
                .orElse(null);
    }

    /**
     * Convert Destination entity to DTO
     */
    private DestinationResponse convertToResponse(Destination destination) {
        return new DestinationResponse(
                destination.getId(),
                destination.getName(),
                destination.getDescription(),
                destination.getRating() != null ? destination.getRating().doubleValue() : 0.0,
                destination.getReviewCount() != null ? destination.getReviewCount() : 0,
                destination.getImageUrl());
    }
}
