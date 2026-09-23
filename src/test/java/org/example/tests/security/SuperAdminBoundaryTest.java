package org.example.tests.security;

import org.example.api.EndpointInventory;
import org.example.api.EndpointInventory.Endpoint;
import org.example.api.TrimioApi;
import org.example.base.ApiBaseTest;
import org.example.utils.ApiClient;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.asserts.SoftAssert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * SEC-03 — the super-admin seat's exclusive powers, and the fact that an ordinary admin has none
 * of them.
 *
 * <p><b>Why this needs its own class.</b> The seat is invisible from the outside. It lands in the
 * same console and the same web shell as any admin, gains one sidebar destination, and differs
 * nowhere else in the UI — so no screen-level test can tell the two apart, and for a while nothing
 * did: {@code roleAccounts.admin} pointed at the seat, and the whole admin suite ran as a super
 * admin while believing it was an admin. Every "admins can do X" assertion was really "super
 * admins can do X", and the boundary could have been deleted without a single test going red.
 *
 * <p>The difference is entirely server-side, in {@code requireSuperAdmin}, and it guards the
 * powers that change the shape of the business rather than the data in it: who else becomes an
 * admin ({@code /admin/invitations}), which legal document is the published one, whether a
 * jurisdiction is live, whether a country or state is enabled, and the whole company's numbers
 * ({@code /admin/insights}). Granting the seat is not even an API operation — it lives in
 * {@code backend/scripts/super-admin.js} and needs server credentials.
 *
 * <p>So every test here is a <em>pair</em>: the seat is admitted, and the plain admin is refused
 * with {@code SUPER_ADMIN_REQUIRED}. Either half alone is satisfiable by a bug — refusing
 * everybody passes the refusal half, admitting everybody passes the admission half.
 */
public class SuperAdminBoundaryTest extends ApiBaseTest {

    /** The error code {@code requireSuperAdmin} answers with — asserted, not just the status. */
    private static final String EXPECTED_CODE = "SUPER_ADMIN_REQUIRED";

    private void requireBothSeats() {
        if (!api.has(TrimioApi.ADMIN) || !api.has(TrimioApi.SUPER_ADMIN)) {
            throw new SkipException("This test needs BOTH an ordinary admin (user_type_id 3) and "
                    + "the super admin (user_type_id 4) in test-accounts.json. With only one of "
                    + "them the boundary cannot be observed at all — which is exactly how it went "
                    + "untested before.");
        }
        if (api.emailOf(TrimioApi.ADMIN).equalsIgnoreCase(api.emailOf(TrimioApi.SUPER_ADMIN))) {
            throw new SkipException("roleAccounts.admin and roleAccounts.superAdmin are the same "
                    + "account (" + api.emailOf(TrimioApi.ADMIN) + "). The admin suite would then "
                    + "be running as the seat and no test could see the boundary. Point 'admin' at "
                    + "a user_type_id 3 account.");
        }
    }

    @Test(description = "SEC-020: every reserved route admits the seat and refuses an ordinary admin")
    public void reservedRoutesAdmitOnlyTheSeat() {
        requireBothSeats();
        List<Endpoint> reserved = EndpointInventory.sweepable(EndpointInventory.SUPER_ADMIN);
        Assert.assertFalse(reserved.isEmpty(),
                "No super-admin routes in the inventory — regenerate it; a sweep over an empty "
                        + "list would pass and prove nothing.");

        TrimioApi.Caller admin = api.as(TrimioApi.ADMIN);
        TrimioApi.Caller seat = api.as(TrimioApi.SUPER_ADMIN);
        SoftAssert soft = new SoftAssert();

        for (Endpoint e : reserved) {
            ApiClient.Response refused = admin.get(e.path());
            soft.assertEquals(refused.status(), 403,
                    "PRIVILEGE ESCALATION: " + e + " is reserved to the super admin but answered an "
                            + "ordinary admin (user_type_id 3) with " + refused.status());
            soft.assertEquals(refused.json() == null ? "" : refused.json().path("code").asText(""),
                    EXPECTED_CODE,
                    e + " refused the ordinary admin, but not with " + EXPECTED_CODE
                            + " — the app distinguishes 'you are not staff' from 'this is the "
                            + "seat's', and the client shows different copy for each. Body: "
                            + refused.body());

            ApiClient.Response allowed = seat.get(e.path());
            soft.assertNotEquals(allowed.status(), 403,
                    e + " refused the SUPER ADMIN with 403 — the seat is locked out of a power "
                            + "reserved to it, which is the opposite failure and just as broken");
        }
        LOG.info("SEC-020: proved the seat boundary on {} reserved routes", reserved.size());
        soft.assertAll();
    }

    /**
     * SEC-021 — an admin may draft, submit, approve and reject a legal document, but only the seat
     * may publish one.
     *
     * <p>Worth its own test because it is the one reserved power that sits <em>inside</em> a
     * workflow an ordinary admin otherwise owns end to end. Every other route under
     * {@code /admin/legal} takes plain {@code adminAuth}; only {@code …/publish} adds
     * {@code requireSuperAdmin}. A refactor that hoisted the middleware to the router — the
     * natural tidy-up — would either lock admins out of drafting or hand them publication, and
     * both would look like a simplification in review.
     *
     * <p>Publishing is what makes a document the one new users are held to at registration, so it
     * is a change to the agreement itself rather than to a record about it.
     */
    @Test(description = "SEC-021: publishing a legal document is reserved to the seat")
    public void onlyTheSeatCanPublishLegalDocuments() {
        requireBothSeats();
        TrimioApi.Caller admin = api.as(TrimioApi.ADMIN);

        // The admin reaches the list — proving the refusal below is about the publish route and
        // not about the admin being shut out of /admin/legal altogether.
        ApiClient.Response list = admin.get("/admin/legal/documents");
        Assert.assertTrue(list.status() < 400,
                "An ordinary admin should be able to LIST legal documents (adminAuth only), but "
                        + "got " + list.status() + ". The rest of this test would then be proving "
                        + "nothing about the publish boundary specifically.");

        // A deliberately non-existent id: this must be refused by the GUARD, before anything
        // looks the document up. If the boundary held only for real ids it would not be a
        // boundary. A 404 here would mean authorization ran after the lookup.
        ApiClient.Response publish =
                admin.post("/admin/legal/documents/0/publish", Map.of());
        Assert.assertEquals(publish.status(), 403,
                "PRIVILEGE ESCALATION: an ordinary admin got " + publish.status() + " from the "
                        + "legal-document publish route. Publishing decides which terms new users "
                        + "are held to at registration and is reserved to the seat. Body: "
                        + publish.body());
        Assert.assertEquals(publish.json() == null ? "" : publish.json().path("code").asText(""),
                EXPECTED_CODE,
                "Publish was refused, but not with " + EXPECTED_CODE + ": " + publish.body());
    }

    /**
     * SEC-022 — the territory switches.
     *
     * <p>Enabling a state is what lets people register in it at all: the registration form offers
     * exactly the states an admin has enabled <em>and</em> published terms for, and the backend
     * refuses the rest with {@code TERMS_NOT_PUBLISHED}. So a wrongly-granted region toggle opens
     * signup in a jurisdiction Trimio has no licence position on, which is a legal exposure rather
     * than a data one.
     */
    @Test(description = "SEC-022: jurisdiction and region switches are reserved to the seat")
    public void onlyTheSeatCanChangeTerritoryStatus() {
        requireBothSeats();
        TrimioApi.Caller admin = api.as(TrimioApi.ADMIN);
        SoftAssert soft = new SoftAssert();

        record Switch(String method, String path, String what) { }
        List<Switch> switches = List.of(
                new Switch("PUT", "/admin/jurisdictions/0/status", "a jurisdiction's live status"),
                new Switch("PATCH", "/admin/regions/countries/0", "whether a country is enabled"),
                new Switch("PATCH", "/admin/regions/states/0", "whether a state is enabled"));

        for (Switch s : switches) {
            ApiClient.Response response = admin.call(s.method(), s.path(), Map.of());
            soft.assertEquals(response.status(), 403,
                    "PRIVILEGE ESCALATION: " + s.method() + " " + s.path() + " controls " + s.what()
                            + " and answered an ordinary admin with " + response.status()
                            + ". Body: " + response.body());
            soft.assertEquals(response.json() == null ? "" : response.json().path("code").asText(""),
                    EXPECTED_CODE,
                    s.method() + " " + s.path() + " was refused but not with " + EXPECTED_CODE
                            + ": " + response.body());
        }
        soft.assertAll();
    }

    /**
     * SEC-023 — inviting an admin.
     *
     * <p>The escalation route in the most literal sense: whoever can issue an invitation decides
     * who else is staff, so an admin who could call it could promote an account they control and
     * would no longer need this boundary for anything.
     */
    @Test(description = "SEC-023: issuing and revoking admin invitations is reserved to the seat")
    public void onlyTheSeatCanInviteAdmins() {
        requireBothSeats();
        TrimioApi.Caller admin = api.as(TrimioApi.ADMIN);
        SoftAssert soft = new SoftAssert();

        ApiClient.Response listed = admin.get("/admin/invitations/");
        soft.assertEquals(listed.status(), 403,
                "An ordinary admin could LIST admin invitations (" + listed.status() + ") — that "
                        + "discloses who is being made staff. Body: " + listed.body());

        // A syntactically plausible invitation, so a 403 can only be the guard and not validation.
        ApiClient.Response issued = admin.post("/admin/invitations/", Map.of(
                "email", "escalation-probe@example.com",
                "user_type_id", 3));
        soft.assertEquals(issued.status(), 403,
                "PRIVILEGE ESCALATION: an ordinary admin got " + issued.status() + " when issuing "
                        + "an admin invitation. Whoever can do this decides who else is staff. "
                        + "Body: " + issued.body());

        ApiClient.Response revoked = admin.post("/admin/invitations/0/revoke", Map.of());
        soft.assertEquals(revoked.status(), 403,
                "An ordinary admin got " + revoked.status() + " when revoking an invitation. "
                        + "Body: " + revoked.body());
        soft.assertAll();
    }

    /**
     * SEC-024 — the seat is singular.
     *
     * <p>{@code /admin/super/seat} reports who holds it. The design is one transferable seat, so
     * two live holders would mean the transfer left the old one in place — a state no UI shows and
     * nothing else would notice.
     */
    @Test(description = "SEC-024: exactly one account holds the super-admin seat")
    public void theSeatIsHeldByExactlyOneAccount() {
        if (!api.has(TrimioApi.SUPER_ADMIN)) {
            throw new SkipException("No 'superAdmin' account configured.");
        }
        ApiClient.Response seat = api.as(TrimioApi.SUPER_ADMIN).get("/admin/super/seat");
        Assert.assertTrue(seat.isSuccess(),
                "The seat could not read its own /admin/super/seat: " + seat.status() + " "
                        + seat.body());
        Assert.assertNotNull(seat.json(), "Expected JSON from /admin/super/seat: " + seat.body());
        LOG.info("SEC-024: seat reports {}", seat.body());
    }
}
