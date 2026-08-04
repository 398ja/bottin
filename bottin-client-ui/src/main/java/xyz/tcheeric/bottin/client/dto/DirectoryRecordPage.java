package xyz.tcheeric.bottin.client.dto;

import java.util.List;

/**
 * The slice of {@code GET /api/v1/records} that profile search reads.
 *
 * <p>Deliberately narrower than the directory's own response: a search result
 * needs the identifier, the key and whether the record is live, not the
 * timestamps, the id or the relay list. The converter ignores the fields left
 * out, so the directory can grow its response without breaking search.
 *
 * @param content the records on the requested page, never null
 */
public record DirectoryRecordPage(List<DirectoryRecord> content) {

    public DirectoryRecordPage {
        content = content == null ? List.of() : List.copyOf(content);
    }

    public static DirectoryRecordPage empty() {
        return new DirectoryRecordPage(List.of());
    }

    /**
     * One registered handle.
     *
     * @param nip05    the full identifier, {@code username@domain}
     * @param username the local part
     * @param pubkey   the key this domain vouches for, canonical hex
     * @param enabled  whether {@code /.well-known/nostr.json} still serves it
     */
    public record DirectoryRecord(String nip05, String username, String pubkey, boolean enabled) {
    }
}
