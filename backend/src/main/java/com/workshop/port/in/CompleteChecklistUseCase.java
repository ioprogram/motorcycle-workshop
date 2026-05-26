package com.workshop.port.in;

import com.workshop.domain.model.AppUser;
import com.workshop.domain.model.InspectionChecklist;

import java.util.UUID;

public interface CompleteChecklistUseCase {

    InspectionChecklist updateChecklist(Command command);

    record Command(
            UUID orderId,
            Boolean visualInspection,
            Boolean engineAndFluidCheck,
            Boolean brakeAndSuspensionCheck,
            Boolean electricalSystemCheck,
            Boolean mileageAndWearAssessment,
            AppUser principal
    ) {}
}
