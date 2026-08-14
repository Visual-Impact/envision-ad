package com.envisionad.webservice.business.dataaccesslayer;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class Business {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private BusinessIdentifier businessId;

    private String name;

    private String ownerId;

    @Enumerated(EnumType.STRING)
    private OrganizationSize organizationSize;

    // TODO - Remove Address Class from business subdomain, and convert it into just a String.
    //  Separate fields for country, province, city, etc. will still be entered separately in the front-end,
    //  but just concatenated into an address string once sent to the backend
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "address_id", referencedColumnName = "id")
    private Address address;

    @Embedded
    private Roles roles;

    private boolean verified;

    // Hook for P4's exclusion logic (nullable = no business type set); this brief only
    // stores it. FK references venue(venue_id), not venue.id — see the P5 migration.
    @Column(name = "business_type_venue_id")
    private String businessTypeVenueId;

    // Defaults true so every existing creation path (self-service createBusiness, the
    // admin-account flow) produces an active business unless a P5 admin explicitly
    // deactivates it afterward — deactivation is opt-in, not a side effect of creation.
    private boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime dateCreated;
}
