package ma.fstg.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired UserDetailsService userDetailsService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void accueilEstPublic() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Espace Utilisateur")));
    }

    @Test
    void pagesEtStylesPublicsSontDisponibles() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mockMvc.perform(get("/css/style.css")).andExpect(status().isOk());
    }

    @Test
    void accesAnonymeAuxEspacesEstRedirigeVersLogin() throws Exception {
        mockMvc.perform(get("/user/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void compteUserPeutConsulterSonEspace() throws Exception {
        mockMvc.perform(get("/user/dashboard").with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("user_dashboard"))
                .andExpect(content().string(containsString("Bonjour")));
    }

    @Test
    void compteUserNePeutPasConsulterEspaceAdmin() throws Exception {
        mockMvc.perform(get("/admin/dashboard").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void compteAdminPeutConsulterLesDeuxEspaces() throws Exception {
        mockMvc.perform(get("/admin/dashboard").with(user("admin").roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin_dashboard"));
        mockMvc.perform(get("/user/dashboard").with(user("admin").roles("ADMIN", "USER")))
                .andExpect(status().isOk());
    }

    @Test
    void formulaireAvecIdentifiantsValidesSeConnecte() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "user").param("password", "1111"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "admin").param("password", "1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void mauvaisMotDePasseAfficheErreur() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "user").param("password", "incorrect"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("invalide")));
    }

    @Test
    void connexionSansTokenCsrfEstRefusee() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "user").param("password", "1111"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deconnexionPostAvecCsrfRedirigeVersAccueil() throws Exception {
        mockMvc.perform(post("/logout").with(user("user").roles("USER")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void deconnexionSansCsrfEstRefusee() throws Exception {
        mockMvc.perform(post("/logout").with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void motsDePasseSontHachesAvecBcrypt() {
        String userHash = userDetailsService.loadUserByUsername("user").getPassword();
        String adminHash = userDetailsService.loadUserByUsername("admin").getPassword();
        assertTrue(userHash.startsWith("$2"));
        assertTrue(adminHash.startsWith("$2"));
        assertNotEquals("1111", userHash);
        assertNotEquals("1234", adminHash);
        assertTrue(passwordEncoder.matches("1111", userHash));
        assertTrue(passwordEncoder.matches("1234", adminHash));
    }
}
