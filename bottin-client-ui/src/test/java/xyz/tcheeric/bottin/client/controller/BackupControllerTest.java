package xyz.tcheeric.bottin.client.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests the backup/restore page routes. Backup export and restore are performed in
 * the browser against the encrypted identity in localStorage, so there are no
 * server-side backup endpoints to test.
 */
@WebMvcTest(BackupController.class)
class BackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Tests that the restore route renders the backup page within the shared layout.
     */
    @Test
    void shouldShowRestorePage() throws Exception {
        mockMvc.perform(get("/restore"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "backup"));
    }

    /**
     * Tests that the backup settings route renders the backup page within the shared layout.
     */
    @Test
    void shouldShowBackupSettingsPage() throws Exception {
        mockMvc.perform(get("/settings/backup"))
                .andExpect(status().isOk())
                .andExpect(view().name("layout"))
                .andExpect(model().attribute("content", "backup"));
    }
}
