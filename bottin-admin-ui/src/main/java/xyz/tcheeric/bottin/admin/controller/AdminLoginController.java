package xyz.tcheeric.bottin.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import xyz.tcheeric.bottin.admin.security.AdminKeyState;
import xyz.tcheeric.bottin.admin.security.ConfiguredAdminAclResolver;

/**
 * Renders the administrator sign-in page.
 *
 * <p>Public by necessity: somebody who is not yet signed in has to reach it.
 *
 * <p>It reports the deployment's key configuration so the page can offer a form
 * only when one could succeed. Reading that from the same resolver that makes
 * the access decision is deliberate — a page inviting somebody to sign in while
 * the resolver refuses everybody would leave an operator unable to tell a wrong
 * key from a missing one.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminLoginController {

    private final ConfiguredAdminAclResolver adminAclResolver;

    @GetMapping("/login")
    public String loginPage(Model model) {
        AdminKeyState keyState = adminAclResolver.keyState();
        model.addAttribute("keyState", keyState.name());
        model.addAttribute("deploymentReady", keyState == AdminKeyState.CONFIGURED);
        return "admin/login";
    }
}
