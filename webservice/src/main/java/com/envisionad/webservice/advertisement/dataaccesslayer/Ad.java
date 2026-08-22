package com.envisionad.webservice.advertisement.dataaccesslayer;

import com.envisionad.webservice.venue.dataaccesslayer.Venue;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;



@Entity
@Data
@NoArgsConstructor
@Table(name="ads")
public class Ad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Embedded
    private AdIdentifier adIdentifier;

    @Column(name = "name")
    private String name;

    @Column(name = "ad_url")
    private String adUrl;

    @Column(name = "ad_type")
    @Enumerated(EnumType.STRING)
    private AdType adType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ad_campaign_ref_id", nullable = false)
    @ToString.Exclude // prevents infinite loops in Lombok-generated toString methods
    private AdCampaign campaign;

    /**
     * Venue types this creative is intended for. Empty means universal — suitable for
     * every venue type — which is the default for a newly created ad. There is no third
     * state: "explicitly tagged for zero venues" is indistinguishable from "untagged".
     *
     * <p>referencedColumnName is required because Venue's @Id is its serial {@code id},
     * while this join table (like media.venue_id) references the public {@code venue_id}.
     *
     * <p>@BatchSize, not a fetch join: this collection is mapped as a List (a bag), and
     * AdCampaign.ads is a bag too. AdCampaignRepository.findByCampaignIdWithAds already
     * does "left join fetch c.ads", so adding a second bag fetch would throw
     * MultipleBagFetchException at bootstrap. Batching collapses the per-ad lazy loads
     * on the campaign-list endpoint into one IN query per 50 ads instead.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ad_venue_tags",
            joinColumns = @JoinColumn(name = "ad_id"),
            inverseJoinColumns = @JoinColumn(name = "venue_id", referencedColumnName = "venue_id")
    )
    @BatchSize(size = 50)
    @ToString.Exclude
    private List<Venue> venues = new ArrayList<>();

}
