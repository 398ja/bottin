package xyz.tcheeric.bottin.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side configuration bound to the {@code bottin.client} prefix: the
 * default relay set, provided by {@code BOTTIN_DEFAULT_RELAYS} as a
 * comma-separated list of {@code wss://} URLs, and the Blossom media server
 * the browser uploads profile images to, provided by {@code BOTTIN_BLOSSOM_URL}.
 */
@Component
@ConfigurationProperties(prefix = "bottin.client")
@Getter
@Setter
public class ClientProperties {

    private List<String> defaultRelays = new ArrayList<>();
    private String blossomUrl;
}
