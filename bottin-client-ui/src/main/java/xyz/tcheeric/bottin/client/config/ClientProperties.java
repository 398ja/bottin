package xyz.tcheeric.bottin.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Client-side configuration bound to the {@code bottin.client} prefix: the
 * handle suffix offered during onboarding, and the address and credentials this
 * server uses to reach the bottin directory API.
 *
 * <p>These are bootstrap values — they are how this process reaches the things
 * that hold everything else. The media server and the relay sets are not here:
 * they are operational data an administrator maintains at {@code /admin/settings}
 * and this server reads through
 * {@link xyz.tcheeric.bottin.client.service.DirectorySettingsClient}.
 */
@Component
@ConfigurationProperties(prefix = "bottin.client")
@Getter
@Setter
public class ClientProperties {

    private String domain;
    private String directoryUrl;
    private String directoryUsername;
    private String directoryPassword;
}
