package org.example.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The backend's whole HTTP surface, loaded from {@code testdata/api/endpoints.json}.
 *
 * <p>Having it as data rather than as hand-written lists is what lets the security suites be
 * exhaustive instead of representative. There are 440 routes across 57 router files; a
 * hand-maintained list of "the important ones" would have drifted from the server within a
 * release, and the route that quietly lost its guard is exactly the one nobody thought to add.
 * The sweeps iterate everything and the assertions are about the <em>guard</em>, so a new route
 * is covered the moment the inventory is regenerated ({@code python3 scripts/harvest_api.py}).
 *
 * <p>{@link Endpoint#guard()} is the authorization class the mount chain implies. It comes from
 * static analysis of {@code server.js} plus the nested {@code router.use} mounts, with one
 * correction applied by hand and recorded in the generator: {@code /v1/admin/enforcements} takes
 * its {@code adminAuth} from a bare {@code app.use(prefix, adminAuth)} that names no router, which
 * nothing reading the route file can see.
 */
public final class EndpointInventory {

    private static final String RESOURCE = "testdata/api/endpoints.json";
    private static List<Endpoint> endpoints;

    /** Authorization classes, ordered from least to most privileged. */
    public static final String PUBLIC = "PUBLIC";
    public static final String AUTH = "AUTH";
    public static final String PRO = "PRO";
    public static final String VENDOR = "VENDOR";
    public static final String STAFF = "STAFF";
    public static final String ADMIN = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * One mounted route.
     *
     * @param method        HTTP verb
     * @param path          full path including the mount prefix
     * @param guard         one of the authorization classes above
     * @param router        the route file it came from, for locating it in the backend
     * @param parameterised whether the path carries a {@code :param} segment
     */
    public record Endpoint(String method, String path, String guard, String router,
                           boolean parameterised) {

        /** True when reaching this route needs no credentials at all. */
        public boolean isPublic() {
            return PUBLIC.equals(guard);
        }

        /**
         * True when this route is safe for an unattended sweep to call.
         *
         * <p>Only parameterless GETs qualify. A write verb would change the shared database the
         * functional suites are asserting against, and a {@code :param} path needs an id a sweep
         * cannot invent — a made-up one exercises the 404 branch, not the guard.
         */
        public boolean isSweepable() {
            return "GET".equals(method) && !parameterised;
        }

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    private EndpointInventory() {
        // static holder
    }

    /** Every mounted route, loaded once. */
    public static synchronized List<Endpoint> all() {
        if (endpoints != null) {
            return endpoints;
        }
        try (InputStream in = EndpointInventory.class.getClassLoader()
                .getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Endpoint inventory not found: " + RESOURCE
                        + " — regenerate it with python3 scripts/harvest_api.py");
            }
            JsonNode root = new ObjectMapper().readTree(in);
            List<Endpoint> loaded = new ArrayList<>();
            for (JsonNode e : root.path("endpoints")) {
                loaded.add(new Endpoint(
                        e.path("method").asText(),
                        e.path("path").asText(),
                        e.path("guard").asText(),
                        e.path("router").asText(),
                        e.path("parameterised").asBoolean()));
            }
            endpoints = List.copyOf(loaded);
            return endpoints;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + RESOURCE, e);
        }
    }

    /** Every route whose guard is {@code guard}. */
    public static List<Endpoint> withGuard(String guard) {
        return all().stream().filter(e -> guard.equals(e.guard())).toList();
    }

    /** The parameterless GETs with {@code guard} — what an unattended sweep may safely call. */
    public static List<Endpoint> sweepable(String guard) {
        return all().stream().filter(e -> guard.equals(e.guard()) && e.isSweepable()).toList();
    }

    /** Every parameterless GET, whatever its guard. */
    public static List<Endpoint> sweepable() {
        return all().stream().filter(Endpoint::isSweepable).toList();
    }

    /** True when {@code role} should be admitted to a route guarded at {@code guard}. */
    public static boolean roleSatisfies(String role, String guard) {
        String r = role == null ? "" : role;
        return switch (guard) {
            case PUBLIC -> true;
            case AUTH -> !r.isBlank() && !"anonymous".equals(r) && !"forged".equals(r);
            case PRO -> TrimioApi.PROFESSIONAL.equals(r);
            case VENDOR -> TrimioApi.VENDOR.equals(r);
            // requireStaff admits admin, super admin and the support tiers (STAFF_TYPE_IDS
            // = 3,4,5,6,7,8 in backend/middleware/requireStaff.js).
            case STAFF -> TrimioApi.ADMIN.equals(r) || TrimioApi.SUPER_ADMIN.equals(r)
                    || TrimioApi.SUPPORT.equals(r);
            // adminAuth admits BOTH admin ids (ADMIN_TYPE_IDS = {3, 4}) — the seat can do
            // everything an admin can. Support is NOT admitted here, which is the boundary
            // SuperAdminBoundaryTest and the matrix both lean on.
            case ADMIN -> TrimioApi.ADMIN.equals(r) || TrimioApi.SUPER_ADMIN.equals(r);
            case SUPER_ADMIN -> TrimioApi.SUPER_ADMIN.equals(r);
            default -> throw new IllegalArgumentException("Unknown guard: " + guard);
        };
    }

    /** A stable, readable id for a route, for use in report names. */
    public static String idFor(Endpoint e) {
        return (e.method() + e.path())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
