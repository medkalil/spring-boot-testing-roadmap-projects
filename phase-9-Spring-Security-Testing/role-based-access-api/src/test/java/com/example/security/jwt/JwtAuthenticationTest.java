package com.example.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * =====================================================================
 * LAYER 3 — AUTHENTICATION TESTS (login -> JWT -> protected endpoint)
 * =====================================================================
 *
 * Layer 1 (UserControllerSecurityTest) answers:
 *     "IF this user were authenticated, would authorization allow the request?"
 *     It uses @WithMockUser, which FORGES the authentication and skips login,
 *     passwords, tokens and the JWT filter entirely.
 *
 * This file answers the question above it:
 *     "Does the ACTUAL authentication mechanism of this API work?"
 *
 *        login
 *          |
 *          |  AuthenticationManager -> BCrypt password check
 *          v
 *        JWT (HS256 signed with app.jwtSecret, carries sub + authorities)
 *          |
 *          |  Authorization: Bearer <token>
 *          v
 *        AuthTokenFilter -> SecurityContext -> authorization matrix
 *
 * WHY @SpringBootTest + @AutoConfigureMockMvc AND NOT @WebMvcTest
 *   A slice would have to re-create AuthenticationManager, JwtUtils, the
 *   entry point and the real UserService. Here every bean of the real
 *   application runs: the real password encoder, the real in-memory store,
 *   the real filter chain. This file tests the wiring, not the mocks.
 *   (The store is in-memory and Hibernate gets an in-memory H2, so nothing
 *   external is needed.)
 *
 * THIS FILE MUST NOT USE @WithMockUser
 *   That annotation would defeat its purpose: it injects an Authentication
 *   and the JWT part of the chain would never be exercised. The only
 *   authentication allowed in this file is a token you obtained by logging in
 *   (or one you built yourself on purpose to be invalid).
 *
 * The scenarios to implement are listed inside this class, below the fields.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private ObjectMapper objectMapper;

    /*
     * =====================================================================
     * SCENARIOS TO TEST (write them below, in this class)
     * =====================================================================
     *
     * GROUP A — login, POST /api/auth/login
     *   Every login call is a POST, and CSRF is enabled: ALWAYS .with(csrf()).
     *   Body: {"username":"john","password":"password"}
     *
     *   1.  loginWithValidCredentials    -> 200, jsonPath $.token not empty,
     *                                       $.type = "Bearer", $.username = "john",
     *                                       $.role = "USER"
     *   2.  loginWithWrongPassword       -> 401
     *   3.  loginWithUnknownUsername     -> 401
     *   4.  loginWithoutCsrfIsRejected   -> 403
     *        Same valid body, WITHOUT .with(csrf()). permitAll only skips the
     *        AUTHORIZATION rule - the CSRF filter still runs first. (If you forget
     *        .with(csrf()) on tests 1-3 you will get 403 for all of them and the
     *        failure will point at the wrong layer.)
     *
     * GROUP B — the real flow: login -> JWT -> protected endpoint
     *
     *   5.  loginThenListUsers
     *        step 1: POST /api/auth/login (with csrf) -> capture the token from
     *                the JSON body:
     *                  MvcResult login = mockMvc.perform(...).andReturn();
     *                  String token = objectMapper
     *                      .readTree(login.getResponse().getContentAsString())
     *                      .get("token").asText();
     *        step 2: GET /api/users with header
     *                  .header("Authorization", "Bearer " + token)
     *        expect: step 1 -> 200, step 2 -> 200
     *
     *   6.  adminLoginThenDelete
     *        login as admin -> DELETE /api/users/10 with the token AND .with(csrf())
     *        -> 204
     *        (DELETE with a token but WITHOUT csrf() -> 403 from the CSRF filter,
     *         not from the role rule. Same trap as group A, on the other endpoint.)
     *
     * GROUP C — tokens that must be rejected: all 401, but distinguishable
     *   The entry point answers with different bodies / WWW-Authenticate headers.
     *   Assert them - "it is 401" alone cannot tell a missing token from a forged one.
     *
     *   7.  missingToken                  GET /api/users, no Authorization header
     *                                     -> 401, body $.error = "unauthenticated",
     *                                        NO "invalid_token" in WWW-Authenticate
     *   8.  malformedToken                header "Bearer not-a-jwt"
     *                                     -> 401, WWW-Authenticate contains invalid_token
     *   9.  expiredToken                  Build one on purpose (nobody waits an hour):
     *                                          Date past = new Date(System.currentTimeMillis() - 3600_000);
     *                                          String expired = jwtUtils.generateJwtToken(
     *                                              "john", List.of("ROLE_USER"),
     *                                              past, past);   // issuedAt AND exp in the past
     *                                     -> 401, WWW-Authenticate contains "expired"
     *                                        jwtUtils.validateJwtToken(expired) == false
     *  10.  wronglySignedToken            Valid structure, WRONG key (a secret that is
     *                                     not app.jwtSecret):
     *                                          Jwts.builder().setSubject("admin")
     *                                              .claim("authorities", List.of("ROLE_ADMIN"))
     *                                              .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
     *                                              .signWith(Keys.hmacShaKeyFor(
     *                                                  "some-other-secret-at-least-32-bytes-long!!".getBytes(StandardCharsets.UTF_8)))
     *                                              .compact();
     *                                     -> 401, WWW-Authenticate contains invalid_token,
     *                                        jwtUtils.validateJwtToken(forged) == false
     *                                        This is the test that proves the signature
     *                                        is checked: a forged ADMIN token does NOT
     *                                        get admin rights.
     *
     * GROUP D — authorization still applies to REAL tokens
     *   Layer 1 rules, but with an Authentication that came from a password, not
     *   from @WithMockUser.
     *
     *  11.  userTokenCannotCreate         login john -> POST /api/users with the
     *                                     token + .with(csrf()) + JSON body -> 403
     *  12.  managerTokenCannotDelete      login manager -> DELETE /api/users/10 with
     *                                     the token + .with(csrf()) -> 403
     *  13.  tokenCarriesTheRightClaims    login admin, then assert with jwtUtils:
     *                                         getUserNameFromJwtToken(token)      == "admin"
     *                                         getAuthoritiesFromJwtToken(token)   == List.of("ROLE_ADMIN")
     *                                         validateJwtToken(token)             == true
     *
     * REQUIREMENTS
     *   - Every test method carries @Test.
     *   - Every request that reaches authorization needs .with(csrf()) for POST/DELETE.
     *   - Helper worth writing FIRST: a private String login(String username)
     *     that POSTs the credentials with csrf() and returns $.token - groups B and
     *     D depend on it.
     *   - Extract the token with the autowired objectMapper (see group B), never
     *     by cutting the string with substring().
     *   - Statuses: login 200, bad credentials 401, CSRF missing 403, DELETE ok 204.
     *   - Assert WWW-Authenticate in group C: status alone is not evidence.
     *   - Keep the header comment above; change no production code.
     */

    //  * GROUP A — login, POST /api/auth/login
    //  *   Every login call is a POST, and CSRF is enabled: ALWAYS .with(csrf()).
    //  *   Body: {"username":"john","password":"password"}
    //  *
    //  *   1.  loginWithValidCredentials    -> 200, jsonPath $.token not empty,
    //  *                                       $.type = "Bearer", $.username = "john",
    //  *                                       $.role = "USER"
    //  *   2.  loginWithWrongPassword       -> 401
    //  *   3.  loginWithUnknownUsername     -> 401
    //  *   4.  loginWithoutCsrfIsRejected   -> 403
    //  *        Same valid body, WITHOUT .with(csrf()). permitAll only skips the
    //  *        AUTHORIZATION rule - the CSRF filter still runs first. (If you forget
    //  *        .with(csrf()) on tests 1-3 you will get 403 for all of them and the
    //  *        failure will point at the wrong layer.)

    @Test
    void loginWithValidCredentials() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.username").value("john"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void loginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownUsername() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"unknown\",\"password\":\"password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden());
    }

    //  * GROUP B — the real flow: login -> JWT -> protected endpoint
    //  *
    //  *   5.  loginThenListUsers
    //  *        step 1: POST /api/auth/login (with csrf) -> capture the token from
    //  *                the JSON body:
    //  *                  MvcResult login = mockMvc.perform(...).andReturn();
    //  *                  String token = objectMapper
    //  *                      .readTree(login.getResponse().getContentAsString())
    //  *                      .get("token").asText();
    //  *        step 2: GET /api/users with header
    //  *                  .header("Authorization", "Bearer " + token)
    //  *        expect: step 1 -> 200, step 2 -> 200
    //  *
    //  *   6.  adminLoginThenDelete
    //  *        login as admin -> DELETE /api/users/10 with the token AND .with(csrf())
    //  *        -> 204
    //  *        (DELETE with a token but WITHOUT csrf() -> 403 from the CSRF filter,
    //  *         not from the role rule. Same trap as group A, on the other endpoint.)

    @Test
    void loginThenListUsers() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("john"))
                .andReturn();

        String token = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("token")
                .asText();

        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminLoginThenDelete() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("token")
                .asText();

        mockMvc.perform(delete("/api/users/10")
                .with(csrf())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    //  * GROUP C — tokens that must be rejected: all 401, but distinguishable
    //  *   The entry point answers with different bodies / WWW-Authenticate headers.
    //  *   Assert them - "it is 401" alone cannot tell a missing token from a forged one.
    //  *
    //  *   7.  missingToken                  GET /api/users, no Authorization header
    //  *                                     -> 401, body $.error = "unauthenticated",
    //  *                                        NO "invalid_token" in WWW-Authenticate
    //  *   8.  malformedToken                header "Bearer not-a-jwt"
    //  *                                     -> 401, WWW-Authenticate contains invalid_token
    //  *   9.  expiredToken                  Build one on purpose (nobody waits an hour):
    //  *                                          Date past = new Date(System.currentTimeMillis() - 3600_000);
    //  *                                          String expired = jwtUtils.generateJwtToken(
    //  *                                              "john", List.of("ROLE_USER"),
    //  *                                              past, past);   // issuedAt AND exp in the past
    //  *                                     -> 401, WWW-Authenticate contains "expired"
    //  *                                        jwtUtils.validateJwtToken(expired) == false
    //  *  10.  wronglySignedToken            Valid structure, WRONG key (a secret that is
    //  *                                     not app.jwtSecret):
    //  *                                          Jwts.builder().setSubject("admin")
    //  *                                              .claim("authorities", List.of("ROLE_ADMIN"))
    //  *                                              .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
    //  *                                              .signWith(Keys.hmacShaKeyFor(
    //  *                                                  "some-other-secret-at-least-32-bytes-long!!".getBytes(StandardCharsets.UTF_8)))
    //  *                                              .compact();
    //  *                                     -> 401, WWW-Authenticate contains invalid_token,
    //  *                                        jwtUtils.validateJwtToken(forged) == false
    //  *                                        This is the test that proves the signature
    //  *                                        is checked: a forged ADMIN token does NOT
    //  *                                        get admin rights.

    @Test
    void missingToken() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
    }

    @Test
    void malformedToken() throws Exception {
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
    }

    @Test
    void expiredToken() throws Exception {
        String expiredToken = jwtUtils.generateJwtToken("john", List.of("ROLE_USER"),
                new Date(System.currentTimeMillis() - 3600_000), new Date(System.currentTimeMillis() - 3600_000));
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"The token is expired\""));
    }

    @Test
    void wronglySignedToken() throws Exception {
        String wronglySignedToken = Jwts.builder().setSubject("admin")
                .claim("authorities", List.of("ROLE_ADMIN"))
                .setExpiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(Keys.hmacShaKeyFor(
                        "some-other-secret-at-least-32-bytes-long!!".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + wronglySignedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
    }

    //  * GROUP D — authorization still applies to REAL tokens
    //  *   Layer 1 rules, but with an Authentication that came from a password, not
    //  *   from @WithMockUser.
    //  *
    //  *  11.  userTokenCannotCreate         login john -> POST /api/users with the
    //  *                                     token + .with(csrf()) + JSON body -> 403
    //  *  12.  managerTokenCannotDelete      login manager -> DELETE /api/users/10 with
    //  *                                     the token + .with(csrf()) -> 403
    //  *  13.  tokenCarriesTheRightClaims    login admin, then assert with jwtUtils:
    //  *                                         getUserNameFromJwtToken(token)      == "admin"
    //  *                                         getAuthoritiesFromJwtToken(token)   == List.of("ROLE_ADMIN")
    //  *                                         validateJwtToken(token)             == true

    @Test
    void userTokenCannotCreate() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("token")
                .asText();

        mockMvc.perform(post("/api/users")
                .with(csrf())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden()); // cz john no rights to create users
    }

    @Test
    void managerTokenCannotDelete() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"manager\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("token")
                .asText();

        mockMvc.perform(delete("/api/users/10")
                .with(csrf())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()); // cz manager can't delete users
    }

    @Test
    void tokenCarriesTheRightClaims() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper
                .readTree(loginResult.getResponse().getContentAsString())
                .get("token")
                .asText();

        assertEquals("admin", jwtUtils.getUserNameFromJwtToken(token)); // admin is the user name
        assertEquals(List.of("ROLE_ADMIN"), jwtUtils.getAuthoritiesFromJwtToken(token)); // admin is the role
        assertTrue(jwtUtils.validateJwtToken(token)); // token is valid
    }

}