package com.example.multitenant;
import com.example.multitenant.entity.Project;
import com.example.multitenant.entity.Task;
import com.example.multitenant.jwt.JwtUtils;
import com.example.multitenant.repository.MembershipRepository;
import com.example.multitenant.repository.OrganizationRepository;
import com.example.multitenant.repository.ProjectRepository;
import com.example.multitenant.repository.TaskRepository;
import com.example.multitenant.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;

/**
 * THE test class of Phase 10: a full-stack integration test.
 *
 * Every layer runs for real, in one process, against a real PostgreSQL:
 *
 *   MockMvc HTTP calls  ->  SecurityFilterChain (real JWT filter + entry point)
 *                       ->  @RestController/@Service (real tenant checks)
 *                       ->  JPA/Hibernate    (real SQL, validate() against Flyway)
 *                       ->  PostgreSQL 16    (real constraints, FK cascade)
 *
 * Why @Testcontainers and not an in-memory DB?
 *   The app runs Flyway against PostgreSQL and validate()s the JPA schema
 *   against those migrations. H2's dialect/constraints differ (e.g. the
 *   unique(UQ_MEMBERSHIP_USER_ORG) and ON DELETE CASCADE behaviour that the
 *   tenant tests depend on). A container is the ONLY faithful stand-in.
 *
 * The three failure statuses you keep bumping into - do not mix them up:
 *
 *   401  authentication failed          -> no/bad/expired token, answered by the
 *                                          JwtAuthenticationEntryPoint BEFORE any
 *                                          controller runs. Nothing is checked,
 *                                          not even whether the user exists.
 *   403  authenticated, RIGHT tenant,   -> the resource IS visible to the caller,
 *        wrong ROLE for the operation      but e.g. a MEMBER may not DELETE.
 *   404  authenticated, but the         -> cross-tenant requests land here too:
 *        resource is NOT visible            "as far as tenant A is concerned, the
 *                                           resource of tenant B does not exist".
 *
 * Why is there no @Transactional on the test class?
 *   MockMvc and the real application are separate layers: the HTTP call runs
 *   through the app's own services, each with its own @Transactional boundary.
 *   A test @Transactional would NOT roll back what the services committed in
 *   their own transactions - it would only start one on the test thread whose
 *   work is discarded. So cleanup is explicit instead, in cleanDatabase().
 *
 * The container is static and SHARED by all test methods (Testcontainers
 * starts it once for the class). Each method must therefore arrive at a
 * clean slate - see cleanDatabase() below.
 *
 * webEnvironment = SpringBootTest.WebEnvironment.MOCK : start the Spring context
 * with a mock web environment, which means that the application will not be
 * started as a web application. This is more efficient than starting the
 * application as a web application, and it avoids the overhead of starting and
 * stopping the web application multiple times. it will use the mockMvc instead of
 * the real web environment.
 * 
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class MultiTenantIntegrationTest {

    /**
     * The real database this class runs every test against. postgres:16 is
     * pulled on first run; the app connects to it through @DynamicPropertySource.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    /**
     * The application.properties point at some local PostgreSQL; the tests
     * override them so the whole app (not just one repo) talks to the container.
     * Flyway migrates it on context startup, exactly like production.
     */
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtils jwtUtils;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired UserRepository userRepository;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired MembershipRepository membershipRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired TaskRepository taskRepository;

    /**
     * Full wipe between tests, in child->parent order - the same direction as
     * the FK constraints from the migrations.
     *
     *   RESTART IDENTITY  -> identity columns reset, so "project with id 1" is
     *                        reproducible without inspecting the DB first.
     *   CASCADE           -> truncated tables referencing the CASCADE'd target
     *                        are truncated automatically (tasks follow projects).
     *
     * TRUNCATE in one statement (as here) executes atomically; the whole wipe
     * is a single operation, so there is no half-cleaned state to trip a test.
     */
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE tasks, projects, memberships, organizations, users RESTART IDENTITY CASCADE");
    }

    /**
     * =========================================================================
     *  THE SCENARIOS TO IMPLEMENT
     * =========================================================================
     *
     * Write the helpers FIRST (see the section at the bottom), then the @Test
     * methods. Every request body needs `objectMapper.writeValueAsString` and
     * every authenticated call needs `Authorization: Bearer <token>`.
     *
     * Read an id out of a response with objectMapper.readTree(...).get("id")
     * and then assert it in the very same test - ids do not survive the wipe.
     *
     * GROUP 1 - THE CANONICAL WORKFLOW (one test end to end, the "happy path")
     * -------------------------------------------------------------------------
     *  1. alice registers (POST /api/auth/register {username, email, password})
     *     -> 201, and users table contains exactly 1 row with a BCrypt hash
     *        (asserted via userRepository, NOT by comparing the password).
     *  2. alice logs in (POST /api/auth/login {email, password}) -> 200 with
     *     accessToken AND refreshToken - both now needed by every later call.
     *  3. alice creates organization "A" -> 201. Immediately assert in the DB
     *     that the (user_id, organization_id, role) membership is ADMIN.
     *  4. alice creates project in A -> 201; the response's organizationId
     *     equals the id from step 3.
     *  5. alice creates a task in that project -> 201; the response's
     *     projectId equals the id from step 4. Assert the DB relationships:
     *     task.project.id == project id and project.organization.id == org id.
     *
     * GROUP 2 - AUTHENTICATION
     * -------------------------------------------------------------------------
     *  6. login with a wrong password -> 401 (and thus reveals nothing).
     *  7. register the SAME email again -> 409, and users still has 1 row.
     *  8. GET /api/organizations/1 with NO token -> 401.
     *  9. GET /api/organizations/1 with a garbage token ("Bearer not.a.jwt")
     *     -> 401.
     * 10. GET /api/organizations/1 with an EXPIRED token -> 401 AND the
     *     WWW-Authenticate header contains "expired" (JwtUtils can mint an
     *     expired token on purpose - see its generateJwtToken overload with an
     *     explicit Date).
     *
     * GROUP 3 - ORGANIZATIONS & MEMBERSHIP
     * -------------------------------------------------------------------------
     * 11. bob registers + logs in. alice (ADMIN of A) adds bob as MEMBER
     *     -> 201; bob GET /api/organizations/{A.id} is now 200.
     * 12. alice adds bob AGAIN -> 409 (V3's UQ_MEMBERSHIP_USER_ORG).
     * 13. alice adds an email that was never registered -> 404.
     * 14. bob (only a MEMBER) tries to add someone -> 403 even though bob is
     *     inside the right tenant and can see the organization.
     * 15. GET an organization the caller has nothing to do with -> 404.
     *
     * GROUP 4 - PROJECTS, TASKS & CROSS-TENANT ISOLATION (the point of phase 10)
     * -------------------------------------------------------------------------
     * 16. charlie registers + logs in and creates his OWN organization B and
     *     a project in B (so both tenants now have data that looks the same).
     * 17. charlie GET /api/projects/{A.project} -> 404, and alice GET
     *     /api/projects/{B.project} -> 404: the SAME request answered 404 in
     *     both directions. Run the pair - one direction alone could hide a
     *     fluke.
     * 18. charlie - despite being ADMIN of B - is a non-member in A, so
     *     charlie GET /api/organizations/{A.id} -> 404. Role NEVER beats
     *     tenancy: "ADMIN of somewhere" is irrelevant somewhere else.
     * 19. bob (MEMBER of A) DELETE /api/projects/{A.project} -> 403.
     *     The project is still in the DB (count remains 1).
     * 20. alice (ADMIN of A) DELETE /api/projects/{A.project} -> 204, and the
     *     FK cascade removed the tasks as well: projects count 0 AND tasks
     *     count 0. (Multi-row DB state proves the delete really happened.)
     *
     * GROUP 5 - VALIDATION & TRANSACTIONS (what the DB can teach you)
     * -------------------------------------------------------------------------
     * 21. alice POSTs a project with a blank name -> 400, and projects count
     *     stays exactly 0 - the failed request left NOTHING behind.
     * 22. (If you want more) create a task with a blank title -> 400 with the
     *     same no-write guarantee.
     *
     * Read group 1 .. 20 in order once more: every number is an assertion a
     * real request must satisfy, and most of them are best-verified twice -
     * first via the HTTP status, then via the repository/DB state.
     * =========================================================================
     */

    /**
     * =========================================================================
     *  HELPERS TO WRITE FIRST (same signatures you'll need everywhere)
     * =========================================================================
     *
     * registerUser(username, email, password)  -> POST /api/auth/register, expect isCreated()
     * loginToken(email, password)              -> POST /api/auth/login, read "accessToken"
     * registerAndLogin(user, email, password)  -> both of the above, returns the token
     * createOrganization(token, name)-> POST /api/organizations, returns id
     * createProject(token, orgId, name)         -> POST .../organizations/{id}/projects
     * createTask(token, projectId, title, desc) -> POST .../projects/{id}/tasks
     * addMember(adminToken, orgId, email, role) -> POST .../organizations/{id}/members
     * auth(token)                    -> header("Authorization", "Bearer " + token)
     * json(anyMap)                   -> objectMapper.writeValueAsString(anyMap)
     *
     * Keep them HTTP-shaped (MockMvc returns the status / a parsed id) and DB
     * asserts (repository counts, relationship ids) INSIDE the @Test that
     * performed the action - never move them into a helper that only asserts
     * status. The helper returns, the test verifies.
     * =========================================================================
     */

    private void registerUser(String username, String email, String password) throws Exception {
        var body = Map.of(
            "username", username,
            "email", email,
            "password", password
        );

        mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body)))
            .andExpect(status().isCreated());
    }
    
    private String loginToken(String email, String password) throws Exception {
        var body = Map.of(
            "email", email,
            "password", password
        );

        var response = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body)))
            .andExpect(status().isOk())
            .andReturn();

        var json = JsonPath.parse(response.getResponse().getContentAsString());
        return json.read("accessToken", String.class);
    }

    private String registerAndLogin(String username, String email, String password) throws Exception {
        registerUser(username, email, password);
        return loginToken(email, password);
    }

    private Long createOrganization(String token, String name) throws Exception {
        var body = Map.of(
            "name", name
        );

        var response = mockMvc.perform(post("/api/organizations")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token)
            .content(json(body)))
            .andExpect(status().isCreated())
            .andReturn();

        var json = JsonPath.parse(response.getResponse().getContentAsString());
        return json.read("id", Long.class);
    }

    private long createProject(String token, long orgId, String name) throws Exception {
        var body = Map.of(
            "name", name
        );

        var response = mockMvc.perform(post("/api/organizations/{orgId}/projects", orgId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token)
            .content(json(body)))
            .andExpect(status().isCreated())
            .andReturn();

        var json = JsonPath.parse(response.getResponse().getContentAsString());
        return json.read("id", Long.class);
    }
    
    private long createTask(String token, long projectId, String title, String desc) throws Exception {
        var body = Map.of(
            "title", title,
            "description", desc
        );

        var response = mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token)
            .content(json(body)))
            .andExpect(status().isCreated())
            .andReturn();

        var json = JsonPath.parse(response.getResponse().getContentAsString());
        return json.read("id", Long.class);
    }

    private void addMember(String token, long orgId, String email, String role) throws Exception {
        var body = Map.of(
            "email", email,
            "role", role
        );

        mockMvc.perform(post("/api/organizations/{orgId}/members", orgId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token)
            .content(json(body)))
            .andExpect(status().isCreated());
    }

    private String json(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }


    //  * GROUP 1 - THE CANONICAL WORKFLOW (one test end to end, the "happy path")
    //  * -------------------------------------------------------------------------
    //  *  1. alice registers (POST /api/auth/register {username, email, password})
    //  *     -> 201, and users table contains exactly 1 row with a BCrypt hash
    //  *        (asserted via userRepository, NOT by comparing the password).
    //  *  2. alice logs in (POST /api/auth/login {email, password}) -> 200 with
    //  *     accessToken AND refreshToken - both now needed by every later call.
    //  *  3. alice creates organization "A" -> 201. Immediately assert in the DB
    //  *     that the (user_id, organization_id, role) membership is ADMIN.
    //  *  4. alice creates project in A -> 201; the response's organizationId
    //  *     equals the id from step 3.
    //  *  5. alice creates a task in that project -> 201; the response's
    //  *     projectId equals the id from step 4. Assert the DB relationships:
    //  *     task.project.id == project id and project.organization.id == org id.

    @Test 
    void testAliceCanRegister() throws Exception {
        registerUser("alice", "alice@gmail.com", "alice");
        assertEquals(1, userRepository.count());
    }
    
    @Test
    void testAliceCanLogin() throws Exception {
        var token = registerAndLogin("alice", "alice@gmail.com", "alice");
        assertNotNull(token);
    }

    
    @Test
    void testAliceCanCreateOrganization() throws Exception {
        var token = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long orgId = createOrganization(token, "A");
        assertEquals("ADMIN", membershipRepository.findByUserEmailAndOrganizationId("alice@gmail.com", orgId).orElseThrow().getRole());
    }

    @Test
    void testAliceCanCreateProject() throws Exception {
        var token = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long orgId = createOrganization(token, "A");
        var projectId = createProject(token, orgId, "Project A");
        
        Project project = projectRepository.findById(projectId).orElseThrow();
        assertEquals(project.getOrganization().getId(), orgId);
    }

    @Test
    void testAliceCanCreateTask() throws Exception {
        var token = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long orgId = createOrganization(token, "A");
        var projectId = createProject(token, orgId, "Project A");
        var taskId = createTask(token, projectId, "Task A", "Task A");

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertEquals(task.getProject().getId(), projectId);
        assertEquals(task.getProject().getOrganization().getId(), orgId);
    }


    //  * GROUP 2 - AUTHENTICATION
    //  * -------------------------------------------------------------------------
    //  *  6. login with a wrong password -> 401 (and thus reveals nothing).
    //  *  7. register the SAME email again -> 409, and users still has 1 row.
    //  *  8. GET /api/organizations/1 with NO token -> 401.
    //  *  9. GET /api/organizations/1 with a garbage token ("Bearer not.a.jwt")
    //  *     -> 401.
    //  * 10. GET /api/organizations/1 with an EXPIRED token -> 401 AND the
    //  *     WWW-Authenticate header contains "expired" (JwtUtils can mint an
    //  *     expired token on purpose - see its generateJwtToken overload with an
    //  *     explicit Date).

    @Test
    void testWrongPassword() throws Exception {
        registerUser("alice", "alice@gmail.com", "alice");
        var body = Map.of(
            "email", "alice@gmail.com",
            "password", "wrong"
        );

        mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testRegisterSameEmail() throws Exception {
        registerUser("alice", "alice@gmail.com", "alice");
        assertEquals(1, userRepository.count());

        var body = Map.of(
            "username", "alice",
            "email", "alice@gmail.com",
            "password", "alice"
        );

        mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(body)))
            .andExpect(status().isConflict());

        assertEquals(1, userRepository.count());
    }

    @Test
    void testNoToken() throws Exception {
        mockMvc.perform(get("/api/organizations/1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testGarbageToken() throws Exception {
        mockMvc.perform(get("/api/organizations/1")
            .header("Authorization", "Bearer not.a.jwt"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void testExpiredToken() throws Exception {
        var expiredToken = jwtUtils.generateJwtToken("alice@gmail.com", null, new Date(System.currentTimeMillis() - 1000),new Date(System.currentTimeMillis() - 1000));
        mockMvc.perform(get("/api/organizations/1")
            .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", containsString("expired")));
    }


    //  * GROUP 3 - ORGANIZATIONS & MEMBERSHIP
    //  * -------------------------------------------------------------------------
    //  * 11. bob registers + logs in. alice (ADMIN of A) adds bob as MEMBER
    //  *     -> 201; bob GET /api/organizations/{A.id} is now 200.
    //  * 12. alice adds bob AGAIN -> 409 (V3's UQ_MEMBERSHIP_USER_ORG).
    //  * 13. alice adds an email that was never registered -> 404.
    //  * 14. bob (only a MEMBER) tries to add someone -> 403 even though bob is
    //  *     inside the right tenant and can see the organization.
    //  * 15. GET an organization the caller has nothing to do with -> 404.

    @Test
    void testAddMemberAliceOrg() throws Exception {
        var aliceToken = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long aliceOrgId = createOrganization(aliceToken, "Alice Org");
        var bobToken = registerAndLogin("bob", "bob@gmail.com", "bob");

        var body = Map.of(
            "email", "bob@gmail.com",
            "role", "MEMBER"
        );

        mockMvc.perform(post("/api/organizations/{orgId}/members", aliceOrgId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + aliceToken)
            .content(json(body)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/organizations/{orgId}", aliceOrgId)
            .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/organizations/{orgId}", aliceOrgId)
            .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isOk());
    }

    @Test
    void testAddMemberTwice() throws Exception {
        var aliceToken = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long aliceOrgId = createOrganization(aliceToken, "Alice Org");
        var bobToken = registerAndLogin("bob", "bob@gmail.com", "bob");

        var body = Map.of(
            "email", "bob@gmail.com",
            "role", "MEMBER"
        );

        mockMvc.perform(post("/api/organizations/{orgId}/members", aliceOrgId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + aliceToken)
            .content(json(body)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/organizations/{orgId}/members", aliceOrgId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + aliceToken)
            .content(json(body)))
            .andExpect(status().isConflict());
    }

    @Test
    void testAddMemberNeverRegisteredEmail() throws Exception {
        var aliceToken = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long aliceOrgId = createOrganization(aliceToken, "Alice Org");
        registerAndLogin("bob", "bob@gmail.com", "bob");

        var body = Map.of(
            "email", "neverFoundEmail@gmail.com",
            "role", "MEMBER"
        );

        mockMvc.perform(post("/api/organizations/{orgId}/members", aliceOrgId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + aliceToken)
            .content(json(body)))
            .andExpect(status().isNotFound());
    }

    // calleer must be ADMIN
    @Test
    void testAddMemberBobToken() throws Exception {
        var aliceToken = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long aliceOrgId = createOrganization(aliceToken, "Alice Org");
        var bobToken = registerAndLogin("bob", "bob@gmail.com", "bob");

        var body = Map.of(
            "email", "bob@gmail.com",
            "role", "MEMBER"
        );
        
        // add bob as member of alice's org
        mockMvc.perform(post("/api/organizations/{orgId}/members", aliceOrgId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + aliceToken)
            .content(json(body)))
            .andExpect(status().isCreated());
        
        // bob not ADMIN try to add member -> isForbidden
        var body2 = Map.of(
            "email", "charlie@gmail.com",
            "role", "MEMBER"
        );

        mockMvc.perform(post("/api/organizations/{orgId}/members", aliceOrgId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + bobToken)
            .content(json(body2)))
            .andExpect(status().isForbidden());
    }

    @Test
    void testGetOrganizationNotInvolved() throws Exception {
        var aliceToken = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long aliceOrgId = createOrganization(aliceToken, "Alice Org");
        var bobToken = registerAndLogin("bob", "bob@gmail.com", "bob");
        Long bobOrgId = createOrganization(bobToken, "Bob Org");

        mockMvc.perform(get("/api/organizations/{orgId}", bobOrgId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isNotFound());
    }
    

    //  * GROUP 4 - PROJECTS, TASKS & CROSS-TENANT ISOLATION (the point of phase 10)
    //  * -------------------------------------------------------------------------
    //  * 16. charlie registers + logs in and creates his OWN organization B and
    //  *     a project in B (so both tenants now have data that looks the same).
    //  * 17. charlie GET /api/projects/{A.project} -> 404, and alice GET
    //  *     /api/projects/{B.project} -> 404: the SAME request answered 404 in
    //  *     both directions. Run the pair - one direction alone could hide a
    //  *     fluke.
    //  * 18. charlie - despite being ADMIN of B - is a non-member in A, so
    //  *     charlie GET /api/organizations/{A.id} -> 404. Role NEVER beats
    //  *     tenancy: "ADMIN of somewhere" is irrelevant somewhere else.
    //  * 19. bob (MEMBER of A) DELETE /api/projects/{A.project} -> 403.
    //  *     The project is still in the DB (count remains 1).
    //  * 20. alice (ADMIN of A) DELETE /api/projects/{A.project} -> 204, and the
    //  *     FK cascade removed the tasks as well: projects count 0 AND tasks
    //  *     count 0. (Multi-row DB state proves the delete really happened.)

    @Test
    void testCharlieCreateOrgAndProject() throws Exception {
        var charlieToken = registerAndLogin("charlie", "charlie@gmail.com", "charlie");
        Long charlieOrgId = createOrganization(charlieToken, "Charlie Org");
        var body = Map.of(
            "name", "Charlie Project"
        );
        mockMvc.perform(post("/api/organizations/{orgId}/projects", charlieOrgId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + charlieToken)
            .content(json(body)))
            .andExpect(status().isCreated());
    }

    @Test 
    void testCharlieGetProject404forAliceOrgProject() throws Exception {
        var charlieToken = registerAndLogin("charlie", "charlie@gmail.com", "charlie");
        Long charlieOrgId = createOrganization(charlieToken, "Charlie Org");
        
        var bobToken = registerAndLogin("bob", "bob@gmail.com", "bob");
        Long bobOrgId = createOrganization(bobToken, "Bob Org");

        var projectId = createProject(charlieToken, charlieOrgId, "Charlie Project");
        
        mockMvc.perform(get("/api/projects/{projectId}", projectId)
            .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isNotFound());
    }
    
    @Test
    void testCharlieAdminOfBButNotMemberOfA() throws Exception {
        var charlieToken = registerAndLogin("charlie", "charlie@gmail.com", "charlie");
        Long charlieOrgId = createOrganization(charlieToken, "Charlie Org");
        var bobToken = registerAndLogin("bob", "bob@gmail.com", "bob");
        Long bobOrgId = createOrganization(bobToken, "Bob Org");

        mockMvc.perform(get("/api/organizations/{orgId}", bobOrgId)
            .header("Authorization", "Bearer " + charlieToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void testBobMemberDeleteProject403() throws Exception {
        var aliceToken = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long aliceOrgId = createOrganization(aliceToken, "Alice Org");
        var bobToken = registerAndLogin("bob", "bob@gmail.com", "bob");

        addMember(aliceToken, aliceOrgId, "bob@gmail.com", "MEMBER");

        var projectId = createProject(aliceToken, aliceOrgId, "Alice Project");
        
        mockMvc.perform(delete("/api/projects/{projectId}", projectId)
            .header("Authorization", "Bearer " + bobToken))
            .andExpect(status().isForbidden());
        
        assertEquals(1, projectRepository.count());
    }

    @Test
    void testAliceAdminDeleteProjectAndTasks() throws Exception {
        var aliceToken = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long aliceOrgId = createOrganization(aliceToken, "Alice Org");
        var projectId = createProject(aliceToken, aliceOrgId, "Alice Project");
        createTask(aliceToken, projectId, "Task 1", "Task 1 Desc");
        createTask(aliceToken, projectId, "Task 2", "Task 2 Desc");

        mockMvc.perform(delete("/api/projects/{projectId}", projectId)
            .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/{projectId}", projectId)
            .header("Authorization", "Bearer " + aliceToken))
            .andExpect(status().isNotFound());

        assertEquals(0, projectRepository.count());
        assertEquals(0, taskRepository.count()); // tasks deleted automatically by cascade after project deleted
    }

    //  *
    //  * GROUP 5 - VALIDATION & TRANSACTIONS (what the DB can teach you)
    //  * -------------------------------------------------------------------------
    //  * 21. alice POSTs a project with a blank name -> 400, and projects count
    //  *     stays exactly 0 - the failed request left NOTHING behind.
    //  * 22. (If you want more) create a task with a blank title -> 400 with the
    //  *     same no-write guarantee.

    @Test
    void testAliceProjectBlankName400AndNoProjectInDb() throws Exception {
        var aliceToken = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long aliceOrgId = createOrganization(aliceToken, "Alice Org");
        var body = Map.of(
            "name", ""
        );
        mockMvc.perform(post("/api/organizations/{orgId}/projects", aliceOrgId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + aliceToken)
            .content(json(body)))
            .andExpect(status().isBadRequest());
        
        assertEquals(0, projectRepository.count());
    }

    @Test
    void testAliceTaskBlankTitle400AndNoTaskInDb() throws Exception {
        var aliceToken = registerAndLogin("alice", "alice@gmail.com", "alice");
        Long aliceOrgId = createOrganization(aliceToken, "Alice Org");
        var projectId = createProject(aliceToken, aliceOrgId, "Alice Project");
        var body = Map.of(
            "title", "",
            "description", "Task 1 Desc"
        );
        mockMvc.perform(post("/api/projects/{projectId}/tasks", projectId)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + aliceToken)
            .content(json(body)))
            .andExpect(status().isBadRequest());
        
        assertEquals(0, taskRepository.count());
    }

}