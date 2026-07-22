package xyz.tcheeric.bottin.client.config;

import jakarta.servlet.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class ClientSecurityConfig {

    @Bean
    public FilterRegistrationBean<Filter> napSessionFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new NapAuthFilter());
        registrationBean.addUrlPatterns("/api/v1/follow/*", "/api/v1/block/*",
                "/api/v1/relays/*", "/api/v1/backup/export",
                "/api/v1/publish-contact-list");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    private static class NapAuthFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            chain.doFilter(request, response);
        }
    }
}
