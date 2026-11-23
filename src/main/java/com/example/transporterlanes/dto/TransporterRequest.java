package com.example.transporterlanes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** {@code laneQuotes} may be empty: a transporter that quotes nothing can simply never be selected. */
@Schema(description = "A transporter and its price for each lane it serves.")
public record TransporterRequest(
        @Schema(description = "Client-assigned transporter id, unique within the request.", example = "1", minimum = "1")
        @NotNull @Positive Long id,
        @Schema(description = "Display name; non-blank, at most 200 characters.", example = "Transporter T1")
        @NotBlank @Size(max = 200) String name,
        @Schema(description = "Quotes for the lanes this transporter serves; may be empty. Lanes without a quote "
                + "from this transporter cannot be assigned to it.")
        @NotNull List<@NotNull @Valid LaneQuoteRequest> laneQuotes) {
}
