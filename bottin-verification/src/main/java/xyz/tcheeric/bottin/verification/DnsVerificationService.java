package xyz.tcheeric.bottin.verification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import xyz.tcheeric.bottin.core.model.VerificationMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for verifying domain ownership via DNS TXT records.
 *
 * <p>Looks for a TXT record at {@code _nostr-verification.<domain>} containing
 * the expected verification token.
 */
@Service
@Slf4j
public class DnsVerificationService {

    private static final String TXT_RECORD_PREFIX = "_nostr-verification.";
    private static final String TOKEN_PREFIX = "nostr-verification=";

    @Value("${bottin.verification.dns.timeout-seconds:10}")
    private int timeoutSeconds;

    /**
     * Verifies a domain by checking for the expected TXT record.
     *
     * @param domain the domain to verify
     * @param expectedToken the expected verification token
     * @return the verification result
     */
    public VerificationResult verify(String domain, String expectedToken) {
        String txtRecordName = TXT_RECORD_PREFIX + domain;
        log.debug("dns_verification_start domain={} txt_record={}", domain, txtRecordName);

        try {
            List<String> txtValues = lookupTxtRecords(txtRecordName);

            if (txtValues.isEmpty()) {
                log.debug("dns_verification_no_records domain={}", domain);
                return VerificationResult.failure(
                        VerificationMethod.DNS_TXT,
                        "No TXT records found at " + txtRecordName,
                        TOKEN_PREFIX + expectedToken,
                        null
                );
            }

            String expectedValue = TOKEN_PREFIX + expectedToken;
            for (String txtValue : txtValues) {
                if (txtValue.equals(expectedValue)) {
                    log.info("dns_verification_success domain={}", domain);
                    return VerificationResult.success(
                            VerificationMethod.DNS_TXT,
                            "DNS TXT record verified successfully"
                    );
                }
            }

            String foundValues = String.join(", ", txtValues);
            log.debug("dns_verification_token_mismatch domain={} expected={} found={}",
                    domain, expectedValue, foundValues);
            return VerificationResult.failure(
                    VerificationMethod.DNS_TXT,
                    "TXT record found but token does not match",
                    expectedValue,
                    foundValues
            );

        } catch (TextParseException e) {
            log.error("dns_verification_parse_error domain={} error={}", domain, e.getMessage());
            return VerificationResult.failure(
                    VerificationMethod.DNS_TXT,
                    "Invalid domain name format: " + e.getMessage()
            );
        } catch (Exception e) {
            log.error("dns_verification_error domain={} error={}", domain, e.getMessage(), e);
            return VerificationResult.failure(
                    VerificationMethod.DNS_TXT,
                    "DNS lookup failed: " + e.getMessage()
            );
        }
    }

    /**
     * Looks up TXT records for the given name.
     */
    private List<String> lookupTxtRecords(String name) throws TextParseException {
        List<String> results = new ArrayList<>();

        Lookup lookup = new Lookup(name, Type.TXT);

        Record[] records = lookup.run();
        if (records == null) {
            int result = lookup.getResult();
            if (result == Lookup.HOST_NOT_FOUND || result == Lookup.TYPE_NOT_FOUND) {
                log.debug("dns_lookup_no_records name={} result={}", name, result);
                return results;
            }
            if (result != Lookup.SUCCESSFUL) {
                log.warn("dns_lookup_failed name={} result={} error={}",
                        name, result, lookup.getErrorString());
                return results;
            }
        }

        if (records != null) {
            for (Record record : records) {
                if (record instanceof TXTRecord txtRecord) {
                    List<String> strings = txtRecord.getStrings();
                    // TXT records can have multiple strings, join them
                    String value = String.join("", strings);
                    results.add(value);
                    log.debug("dns_txt_record_found name={} value={}", name, value);
                }
            }
        }

        return results;
    }

    /**
     * Generates instructions for setting up DNS verification.
     *
     * @param domain the domain to verify
     * @param token the verification token
     * @return human-readable instructions
     */
    public String getSetupInstructions(String domain, String token) {
        return String.format("""
            Add a TXT record to your DNS with:

            Name: %s%s
            Type: TXT
            Value: %s%s

            DNS changes may take up to 48 hours to propagate.
            """, TXT_RECORD_PREFIX, domain, TOKEN_PREFIX, token);
    }
}
