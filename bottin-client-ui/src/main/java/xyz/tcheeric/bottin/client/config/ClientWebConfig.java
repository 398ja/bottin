package xyz.tcheeric.bottin.client.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ClientWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // no-cache forces browsers to revalidate (conditional GET) so an updated
        // bundle is fetched instead of being served stale from heuristic caching.
        //
        // Two locations, searched in order: this module's own scripts, then the
        // ones shared with the admin dashboard from bottin-web-assets. Declaring
        // a handler for /js/** at all replaces Spring Boot's defaults, which
        // would otherwise have found META-INF/resources by themselves — so the
        // shared location has to be named explicitly or the shared scripts 404.
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/", "classpath:/META-INF/resources/js/")
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.noCache());
    }
}
