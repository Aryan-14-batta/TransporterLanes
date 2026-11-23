package com.example.transporterlanes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Body of POST /api/v1/transporters/input. Replaces the whole stored dataset. */
@Schema(description = "The complete dataset. Replaces whatever was stored before.")
public record InputRequest(
        @Schema(description = "All lanes; at least one.")
        @NotEmpty List<@NotNull @Valid LaneRequest> lanes,
        @Schema(description = "All transporters with their quotes; at least one.")
        @NotEmpty List<@NotNull @Valid TransporterRequest> transporters) {
}
