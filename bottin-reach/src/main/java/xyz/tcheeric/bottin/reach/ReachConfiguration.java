package xyz.tcheeric.bottin.reach;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activates {@link ReachProperties} binding for the reach module, in both the
 * standalone application and embedded (starter) deployments.
 */
@Configuration
@EnableConfigurationProperties(ReachProperties.class)
public class ReachConfiguration {
}
