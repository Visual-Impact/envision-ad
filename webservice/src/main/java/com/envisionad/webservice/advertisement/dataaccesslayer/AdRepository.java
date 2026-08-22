package com.envisionad.webservice.advertisement.dataaccesslayer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdRepository extends JpaRepository<Ad, Integer> {

    long countByVenues_VenueId(String venueId);

    List<Ad> findByVenues_VenueId(String venueId);

}
