package xyz.tcheeric.bottin.client.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class HomePageIT extends BaseClientIT {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void shouldRedirectRootToOnboarding() {
        var response = rest.getForEntity("/", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Get Started");
    }

    @Test
    void shouldReturnOnboardingPage() {
        var response = rest.getForEntity("/onboarding", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Welcome to Bottin");
    }

    @Test
    void shouldReturnLoginPage() {
        var response = rest.getForEntity("/login", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Sign In");
    }

    @Test
    void shouldReturnSearchPage() {
        var response = rest.getForEntity("/search", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Search Profiles");
    }

    @Test
    void shouldReturnProfilePage() {
        var response = rest.getForEntity("/profile", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("My Profile");
    }

    @Test
    void shouldReturnSettingsPage() {
        var response = rest.getForEntity("/settings", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Settings");
    }
}
