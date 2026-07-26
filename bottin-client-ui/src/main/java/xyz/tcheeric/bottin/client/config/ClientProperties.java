package xyz.tcheeric.bottin.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side configuration bound from the {@code bottin.client} prefix. Only the
 * default relay set is consumed today; it is provided by the
 * {@code BOTTIN_DEFAULT_RELAYS} environment variable as a comma-separated list of
 * {@code wss://} URLs.
 */
@Component
@ConfigurationProperties(prefix = "bottin.client")
@Getter
@Setter
public class ClientProperties {

    private List<String> defaultRelays = new ArrayList<>();
}
