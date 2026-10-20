package xyz.tcheeric.bottin.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * imani-wallet#342: the handle is trimmed and lowercased while the request is deserialised, so
 * bean validation checks the canonical value. Real Jackson, real Hibernate Validator, in the
 * order a {@code @Valid @RequestBody} applies them.
 */
class CreateNip05RecordRequestTest {

    private static final String PUBKEY = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private static CreateNip05RecordRequest fromWire(String username, String domain) throws Exception {
        String json = MAPPER.writeValueAsString(Map.of("username", username, "domain", domain, "pubkey", PUBKEY));
        return MAPPER.readValue(json, CreateNip05RecordRequest.class);
    }

    // A padded or mixed-case handle deserialises as its canonical lowercase form and validates.
    @ParameterizedTest
    @ValueSource(strings = {" alice", "alice ", "Alice", " ALICE "})
    void shouldNormaliseHandleWhenDeserialised(String raw) throws Exception {
        // Act
        CreateNip05RecordRequest request = fromWire(raw, "example.com");

        // Assert
        assertThat(request.getUsername()).isEqualTo("alice");
        assertThat(validator.validate(request)).isEmpty();
    }

    // The domain is canonicalised the same way.
    @Test
    void shouldNormaliseDomainWhenDeserialised() throws Exception {
        assertThat(fromWire("alice", " Example.COM ").getDomain()).isEqualTo("example.com");
    }

    // Normalising never repairs an inner space or a forbidden character: the @Pattern still refuses them.
    @ParameterizedTest
    @ValueSource(strings = {"al ice", "alice!"})
    void shouldStillRejectHandleThatIsInvalidAfterNormalisation(String raw) throws Exception {
        // Act
        Set<ConstraintViolation<CreateNip05RecordRequest>> violations = validator.validate(fromWire(raw, "example.com"));

        // Assert
        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("username");
    }

    // A server-side caller using the builder gets the same canonical value as the wire.
    @Test
    void shouldNormaliseHandleSetThroughBuilder() {
        CreateNip05RecordRequest request = CreateNip05RecordRequest.builder().username(" Bob ").build();

        assertThat(request.getUsername()).isEqualTo("bob");
    }
}
