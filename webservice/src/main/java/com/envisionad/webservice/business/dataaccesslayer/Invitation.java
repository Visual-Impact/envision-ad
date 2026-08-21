package com.envisionad.webservice.business.dataaccesslayer;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class Invitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Embedded
    private InvitationIdentifier invitationId;

    private BusinessIdentifier businessId;

    @Email
    @NotNull
    private String email;

    // Optional — only used to provision a new Auth0 user if the invitee still has no
    // account when the invitation is accepted (P5 FR 3.2).
    private String name;

    private String token;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime timeCreated;

    private LocalDateTime timeExpires;
}
