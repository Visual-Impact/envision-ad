package com.envisionad.webservice.media.DataAccessLayer;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "media_location")
public class MediaLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "media_location_id", nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String province;

    @Column(nullable = false)
    private String city;

    /**
     * Free-text region, optional. Bundle REGION rules match against this the same
     * way CITY rules match against {@link #city}. Deliberately not a taxonomy.
     */
    @Column(name = "region", length = 100)
    private String region;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false, name = "postal_code")
    private String postalCode;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "geocoding_response", columnDefinition = "TEXT")
    private String geocodingResponse;

    /**
     * Not persisted. Set by the request mapper when the media owner placed a pin
     * on the map themselves instead of picking an autocomplete suggestion — tells
     * the service layer to trust the submitted latitude/longitude as-is rather
     * than re-geocoding.
     */
    @Transient
    private Boolean manualCoordinates;

    @OneToMany(mappedBy = "mediaLocation", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    private List<Media> mediaList = new ArrayList<>();
}
