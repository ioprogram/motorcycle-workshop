package com.workshop.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_checklists")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private RepairOrder repairOrder;

    private boolean visualInspection;
    private boolean engineAndFluidCheck;
    private boolean brakeAndSuspensionCheck;
    private boolean electricalSystemCheck;
    private boolean mileageAndWearAssessment;

    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser completedBy;

    @Version
    private Long version;

    public boolean isComplete() {
        return visualInspection
            && engineAndFluidCheck
            && brakeAndSuspensionCheck
            && electricalSystemCheck
            && mileageAndWearAssessment;
    }
}
