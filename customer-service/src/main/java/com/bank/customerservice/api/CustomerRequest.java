package com.bank.customerservice.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.Locale;

@Schema(name = "CustomerRequest")
public record CustomerRequest(
        @NotBlank @Size(max = 100) @Schema(example = "Dragan") String firstName,
        @NotBlank @Size(max = 100) @Schema(example = "Pajic") String lastName,
        @NotBlank @Email @Size(max = 254) @Schema(example = "customer@example.com") String email,
        @NotBlank @Pattern(regexp = "^[+][1-9][0-9]{7,14}$",
                message = "must be an E.164 phone number, for example +38761123456")
        @Schema(example = "+38761123456") String phone) {
    public CustomerRequest {
        firstName = firstName == null ? null : firstName.trim();
        lastName = lastName == null ? null : lastName.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        phone = phone == null ? null : phone.trim();
    }
}
