package com.envisionad.webservice.advertisement.dataaccesslayer;

import com.envisionad.webservice.business.dataaccesslayer.BusinessIdentifier;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@Table(name = "ad_campaigns")
public class AdCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Embedded
    private AdCampaignIdentifier campaignId;

    @Embedded
    private BusinessIdentifier businessId;

    private String name;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "creatives_updated_at")
    private LocalDateTime creativesUpdatedAt;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude // this prevents infinite loops with Lombok
    private List<Ad> ads = new ArrayList<>();

}
