package xyz.tcheeric.bottin.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The administrator-addition form.
 *
 * <p>Validation here is only what the form can judge: that something was typed,
 * and that it is not absurdly long. Whether the value is a public key is decided
 * by the service, which owns the rule and is the only place that can answer it
 * the same way for every caller.
 */
@Data
public class AddAdministratorForm {

    @NotBlank(message = "Enter the administrator's public key.")
    @Size(max = 128, message = "A public key is 63 characters as npub or 64 as hex.")
    private String key;

    @Size(max = 100, message = "Keep the label under 100 characters.")
    private String label;
}
