package com.example.security.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;

import com.example.security.config.SecurityConfig;
import com.example.security.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
/*
 * Phase 9 — Spring Security testing (Layer 1: authorization).
 *
 * THE FILTER CHAIN THESE TESTS EXERCISE
 *
 *   MockMvc.perform(...)
 *        -> Spring Security filter chain   <- we are testing THIS
 *        -> controller
 *        -> @MockitoBean UserService       <- never reached when security denies
 *
 * Two distinct questions, two distinct HTTP statuses:
 *
 *   401 Unauthorized  -> "I don't know who you are."      (anonymous, no Authentication)
 *   403 Forbidden     -> "I know who you are, but you
 *                         are not allowed to do this."    (authenticated, wrong authority)
 *
 * THE MATRIX THIS FILE IS SUPPOSED TO COVER
 *
 *   request   anonymous   USER   MANAGER   ADMIN
 *   GET       401         200    200       200
 *   POST      401         403    201       201
 *   DELETE    401         403    403       204
 *
 * THINGS IT IS EASY TO GET WRONG
 *
 * 1) roles vs authorities
 *      roles = "ADMIN"           -> ROLE_ADMIN   (the ROLE_ prefix is added)
 *      authorities = "ADMIN"     -> ADMIN        (no prefix, hasRole("ADMIN") FAILS)
 *      .hasRole("ADMIN") checks ROLE_ADMIN.
 *
 * 2) CSRF
 *      CSRF is ENABLED in SecurityConfig on purpose. A POST/DELETE sent WITHOUT
 *      .with(csrf()) is rejected by the CSRF filter with 403 BEFORE authorization
 *      runs, so a test can "pass for the wrong reason":
 *
 *          @WithMockUser(roles = "ADMIN")
 *          delete("/api/users/10")        -> 403  (from CsrfFilter, not from the role rule)
 *          delete("/api/users/10").with(csrf())  -> 204
 *
 *      GET is a safe method and never needs .with(csrf()).
 *
 * 3) proving the controller was NOT reached
 *      status 403 alone is not enough. The security value of the negative tests is:
 *
 *          verify(userService, never()).deleteUser(anyLong());
 *
 *      That proves the request died in the filter chain, not in the service.
 *
 * 4) @WithMockUser does NOT test authentication
 *      It injects a ready-made Authentication into the SecurityContext and jumps
 *      straight to the authorization check. Real JWT authentication (login ->
 *      Bearer token -> AuthTokenFilter) is implemented in this project and
 *      tested in Layer 3: src/test/java/com/example/security/jwt/JwtAuthenticationTest.java
 *
 *          @WithMockUser   -> SecurityContext -> authorization rules
 *          real JWT        -> JWT filter -> signature/expiration/claims -> authorities
 *
 * @WebMvcTest loads Spring Security + the controller only. SecurityConfig must be
 * imported explicitly, because @WebMvcTest does not scan plain @Configuration
 * classes. The service is replaced by a Mockito mock, so no database is involved.
 * The 401s below are produced by JwtAuthenticationEntryPoint (the same class
 * that later answers "expired" / "invalid_token" for Layer 3).
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    /*
     * =====================================================================
     * TESTS YOU NEED TO IMPLEMENT (in this class)
     * =====================================================================
     *
     * GROUP 1 — anonymous (no authentication): every status must be 401
     *
     *   1.  anonymousCanGetNothing        get("/api/users")                      -> 401
     *   2.  anonymousCannotCreate         post("/api/users")  with .with(csrf()) -> 401
     *   3.  anonymousCannotDelete         delete("/api/users/10") with .with(csrf()) -> 401
     *
     * GROUP 2 — GET /api/users (USER, MANAGER, ADMIN are all allowed): 200
     *
     *   4.  userCanListUsers              @WithMockUser(roles = "USER")          -> 200
     *   5.  managerCanListUsers           @WithMockUser(roles = "MANAGER")       -> 200
     *   6.  adminCanListUsers             @WithMockUser(roles = "ADMIN")         -> 200
     *
     * GROUP 3 — POST /api/users (MANAGER, ADMIN only)
     *
     *   7.  userCannotCreateUser          roles = "USER", body {"username":"john"},
     *                                     .with(csrf())                          -> 403
     *                                     + verify(userService, never()).createUser(any())
     *   8.  managerCanCreateUser          roles = "MANAGER" + csrf + JSON body   -> 201
     *   9.  adminCanCreateUser            roles = "ADMIN"   + csrf + JSON body   -> 201
     *
     * GROUP 4 — DELETE /api/users/{id} (ADMIN only)
     *
     *  10.  userCannotDeleteUser          roles = "USER"     + csrf              -> 403
     *                                     + verify(userService, never()).deleteUser(anyLong())
     *  11.  managerCannotDeleteUser       roles = "MANAGER"  + csrf              -> 403
     *                                     + verify(userService, never()).deleteUser(anyLong())
     *  12.  adminCanDeleteUser            roles = "ADMIN"    + csrf              -> 204
     *
     * GROUP 5 — CSRF, the trap
     *
     *  13.  postWithoutCsrfIsRejected      roles = "ADMIN", NO .with(csrf())      -> 403
     *  14.  deleteWithoutCsrfIsRejected    roles = "ADMIN", NO .with(csrf())      -> 403
     *      (both are 403 from CsrfFilter, NOT from the role rule — see header)
     *
     * GROUP 6 — the context itself
     *
     *  15.  mockUserHasCorrectAuthority    @WithMockUser(username = "john", roles = "USER"):
     *                                     assert SecurityContextHolder has
     *                                     "john" and authority "ROLE_USER"
     *
     * REQUIREMENTS
     *   - Every test method must be annotated with @Test.
     *   - Every request that reaches authorization needs .with(csrf()) for POST/DELETE.
     *   - Every denied request should ALSO assert the mock was untouched:
     *       verify(userService, never()).deleteUser(anyLong());
     *   - Statuses are asserted with andExpect(status().is...) — never 200 for
     *     POST (201) or DELETE (204).
     *   - Keep the header comment above; add no production code changes.
     */


    //  * GROUP 1 — anonymous (no authentication): every status must be 401
    //  *
    //  *   1.  anonymousCanGetNothing        get("/api/users")                      -> 401
    //  *   2.  anonymousCannotCreate         post("/api/users")  with .with(csrf()) -> 401
    //  *   3.  anonymousCannotDelete         delete("/api/users/10") with .with(csrf()) -> 401
    @Test
    void anonymousCanGetNothing() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotCreate() throws Exception {
        mockMvc.perform(post("/api/users").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotDelete() throws Exception {
        mockMvc.perform(delete("/api/users/10").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    //  * GROUP 2 — GET /api/users (USER, MANAGER, ADMIN are all allowed): 200
    //  *
    //  *   4.  userCanListUsers              @WithMockUser(roles = "USER")          -> 200
    //  *   5.  managerCanListUsers           @WithMockUser(roles = "MANAGER")       -> 200
    //  *   6.  adminCanListUsers             @WithMockUser(roles = "ADMIN")         -> 200
    @WithMockUser(roles = "USER")
    @Test
    void userCanListUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk());
    }

    @WithMockUser(roles = "MANAGER")
    @Test
    void managerCanListUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk());
    }

    //  * GROUP 3 — POST /api/users (MANAGER, ADMIN only)
    //  *
    //  *   7.  userCannotCreateUser          roles = "USER", body {"username":"john"},
    //  *                                     .with(csrf())                          -> 403
    //  *                                     + verify(userService, never()).createUser(any())
    //  *   8.  managerCanCreateUser          roles = "MANAGER" + csrf + JSON body   -> 201
    //  *   9.  adminCanCreateUser            roles = "ADMIN"   + csrf + JSON body   -> 201
    @WithMockUser(roles = "USER")
    @Test
    void userCannotCreateUser() throws Exception {
        mockMvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john\"}"))
                .andExpect(status().isForbidden());

        verify(userService, never()).createUser(any());
    }

    @WithMockUser(roles = "MANAGER")
    @Test
    void managerCanCreateUser() throws Exception {
        mockMvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john\"}"))
                .andExpect(status().isCreated());

        verify(userService).createUser(any());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCanCreateUser() throws Exception {
        mockMvc.perform(post("/api/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john\"}"))
                .andExpect(status().isCreated());

        verify(userService).createUser(any());
    }

    //  * GROUP 4 — DELETE /api/users/{id} (ADMIN only)
    //  *
    //  *  10.  userCannotDeleteUser          roles = "USER"     + csrf              -> 403
    //  *                                     + verify(userService, never()).deleteUser(anyLong())
    //  *  11.  managerCannotDeleteUser       roles = "MANAGER"  + csrf              -> 403
    //  *                                     + verify(userService, never()).deleteUser(anyLong())
    //  *  12.  adminCanDeleteUser            roles = "ADMIN"    + csrf              -> 204
    @WithMockUser(roles = "USER")
    @Test
    void userCannotDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 1L).with(csrf()))
                .andExpect(status().isForbidden());

        verify(userService, never()).deleteUser(anyLong());
    }

    @WithMockUser(roles = "MANAGER")
    @Test
    void managerCannotDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 1L).with(csrf()))
                .andExpect(status().isForbidden());

        verify(userService, never()).deleteUser(anyLong());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void adminCanDeleteUser() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 1L).with(csrf()))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(anyLong());
    }

    //  * GROUP 5 — CSRF, the trap
    //  *
    //  *  13.  postWithoutCsrfIsRejected      roles = "ADMIN", NO .with(csrf())      -> 403
    //  *  14.  deleteWithoutCsrfIsRejected    roles = "ADMIN", NO .with(csrf())      -> 403
    //  *      (both are 403 from CsrfFilter, NOT from the role rule — see header)

    @WithMockUser(roles = "ADMIN")
    @Test
    void postWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john\"}"))
                .andExpect(status().isForbidden());

        verify(userService, never()).createUser(any());
    }

    @WithMockUser(roles = "ADMIN")
    @Test
    void deleteWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", 1L).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john\"}"))
                .andExpect(status().isForbidden());

        verify(userService, never()).deleteUser(anyLong());
    }

    //  * GROUP 6 — the context itself
    //  *
    //  *  15.  mockUserHasCorrectAuthority    @WithMockUser(username = "john", roles = "USER"):
    //  *                                     assert SecurityContextHolder has
    //  *                                     "john" and authority "ROLE_USER"
    // RQ IMPORTANT: because the conetxt used in the mockmvc request, is not necessarily the same context available afterward in your test thread. 
    // that why thi will fail if you run it: becasu authentication will be null (SecurityContextHolder.getContext().getAuthentication()) 
    
    /*
        For learning purposes, you could temporarily create an endpoint specifically for testing:

        @GetMapping("/api/me")
        public Map<String, Object> me(Authentication authentication) {

            return Map.of(
                    "username", authentication.getName(),
                    "authorities", authentication.getAuthorities()
                            .stream()
                            .map(GrantedAuthority::getAuthority)
                            .toList()
            );
        }

        Then:

        @Test
        @WithMockUser(username = "john", roles = "USER")
        void mockUserHasCorrectAuthentication() throws Exception {

            mockMvc.perform(get("/api/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("john"))
                    .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
        }

        This is very explicit:

        @WithMockUser
            ↓
        username = john
        roles = USER
            ↓
        Spring Security creates Authentication
            ↓
        MockMvc request
            ↓
        Controller receives Authentication
            ↓
        username = john
        authority = ROLE_USER
    */
    
    // @WithMockUser(username = "john", roles = "USER")
    // @Test
    // void mockUserHasCorrectAuthority() throws Exception {
    //     mockMvc.perform(get("/api/users"))
    //             .andExpect(status().isOk());

    //     Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    //     assertNotNull(authentication);
    //     assertEquals("john", authentication.getName());
    //     assertTrue(authentication.getAuthorities().stream()
    //             .map(GrantedAuthority::getAuthority)
    //             .anyMatch(authority -> authority.equals("ROLE_USER")));
    // }
}