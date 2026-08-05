package com.envisionad.webservice.media.DataAccessLayer;

import com.envisionad.webservice.media.PresentationLayer.Models.ScheduleModel;
import com.envisionad.webservice.media.PresentationLayer.Models.WeeklyScheduleEntry;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.sql.Types;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = "media")
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "media_id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_location_id", nullable = false)
    private MediaLocation mediaLocation;

    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "media_owner_name", nullable = false)
    private String mediaOwnerName;

    @Column(name = "type_of_display", nullable = false)
    @Enumerated(EnumType.STRING)
    private TypeOfDisplay typeOfDisplay;

    @Column(name = "loop_duration")
    private Integer loopDuration;

    @Column(name = "resolution")
    private String resolution;

    @Column(name = "width")
    private Double width;

    @Column(name = "height")
    private Double height;

    /**
     * Monthly price per screen (CAD), billed via Stripe Billing subscriptions.
     * Superseded weekly-reservation pricing — the column was reinterpreted rather
     * than renamed, so read sites that predate bundles still say "price".
     */
    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "daily_impressions")
    private Integer dailyImpressions;

    @Column(name = "schedule")
    @JdbcTypeCode(SqlTypes.JSON)
    private ScheduleModel schedule;

    @Column(name = "active_days")
    private Integer activeDays;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "image_file_name")
    private String imageFileName;

    @Column(name = "image_content_type")
    private String imageContentType;

    @Column(name = "image_url")
    private String imageUrl;

    @Lob
    @JdbcTypeCode(Types.BINARY)
    @Column(name = "image_data")
    private byte[] imageData;

    @Column(name = "venue_id")
    private String venueId;

    @Column(name = "preview_configuration")
    @JdbcTypeCode(SqlTypes.JSON)
    private String previewConfiguration;

    @PrePersist
    @PreUpdate
    private void recalculateActiveDays() {
        if (schedule == null || schedule.getWeeklySchedule() == null) {
            this.activeDays = 0;
            return;
        }

        this.activeDays = (int) schedule.getWeeklySchedule()
                .stream()
                .filter(WeeklyScheduleEntry::isActive)
                .count();
    }
}
