package com.schemabridge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NaturalLanguageRuleRequest {
    @NotBlank(message = "Instruction cannot be blank")
    private String instruction;
}
