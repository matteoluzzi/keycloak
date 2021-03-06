package org.keycloak.testsuite.oauth;

import org.apache.http.HttpResponse;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.keycloak.audit.Details;
import org.keycloak.audit.Errors;
import org.keycloak.models.ApplicationModel;
import org.keycloak.models.RealmModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.RefreshToken;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.testsuite.AssertEvents;
import org.keycloak.testsuite.OAuthClient;
import org.keycloak.testsuite.rule.KeycloakRule;
import org.keycloak.testsuite.rule.WebResource;
import org.keycloak.testsuite.rule.WebRule;
import org.openqa.selenium.WebDriver;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class ResourceOwnerPasswordCredentialsGrantTest {

    @ClassRule
    public static KeycloakRule keycloakRule = new KeycloakRule(new KeycloakRule.KeycloakSetup() {
        @Override
        public void config(RealmManager manager, RealmModel adminstrationRealm, RealmModel appRealm) {
            ApplicationModel app = appRealm.addApplication("resource-owner");
            app.setSecret("secret");
            appRealm.setPasswordCredentialGrantAllowed(true);
        }
    });

    @Rule
    public AssertEvents events = new AssertEvents(keycloakRule);

    @Rule
    public WebRule webRule = new WebRule(this);

    @WebResource
    protected WebDriver driver;

    @WebResource
    protected OAuthClient oauth;

    @Test
    public void grantAccessToken() throws Exception {
        oauth.clientId("resource-owner");

        OAuthClient.AccessTokenResponse response = oauth.doGrantAccessTokenRequest("secret", "test-user@localhost", "password");

        assertEquals(200, response.getStatusCode());

        AccessToken accessToken = oauth.verifyToken(response.getAccessToken());
        RefreshToken refreshToken = oauth.verifyRefreshToken(response.getRefreshToken());

        events.expectLogin()
                .client("resource-owner")
                .session(accessToken.getSessionState())
                .detail(Details.AUTH_METHOD, "oauth_credentials")
                .detail(Details.RESPONSE_TYPE, "token")
                .detail(Details.TOKEN_ID, accessToken.getId())
                .detail(Details.REFRESH_TOKEN_ID, refreshToken.getId())
                .removeDetail(Details.CODE_ID)
                .removeDetail(Details.REDIRECT_URI)
                .assertEvent();

        assertEquals(accessToken.getSessionState(), refreshToken.getSessionState());

        OAuthClient.AccessTokenResponse refreshedResponse = oauth.doRefreshTokenRequest(response.getRefreshToken(), "secret");

        AccessToken refreshedAccessToken = oauth.verifyToken(refreshedResponse.getAccessToken());
        RefreshToken refreshedRefreshToken = oauth.verifyRefreshToken(refreshedResponse.getRefreshToken());

        assertEquals(accessToken.getSessionState(), refreshedAccessToken.getSessionState());
        assertEquals(accessToken.getSessionState(), refreshedRefreshToken.getSessionState());

        events.expectRefresh(refreshToken.getId(), refreshToken.getSessionState()).client("resource-owner").assertEvent();
    }

    @Test
    public void grantAccessTokenNotEnabled() throws Exception {
        try {
            keycloakRule.update(new KeycloakRule.KeycloakSetup() {
                @Override
                public void config(RealmManager manager, RealmModel adminstrationRealm, RealmModel appRealm) {
                    appRealm.setPasswordCredentialGrantAllowed(false);
                }
            });

            oauth.clientId("resource-owner");

            OAuthClient.AccessTokenResponse response = oauth.doGrantAccessTokenRequest("secret", "test-user@localhost", "password");

            assertEquals(403, response.getStatusCode());
            assertEquals("not_enabled", response.getError());

        } finally {
            keycloakRule.update(new KeycloakRule.KeycloakSetup() {
                @Override
                public void config(RealmManager manager, RealmModel adminstrationRealm, RealmModel appRealm) {
                    appRealm.setPasswordCredentialGrantAllowed(true);
                }
            });
        }
    }

    @Test
    public void grantAccessTokenLogout() throws Exception {
        oauth.clientId("resource-owner");

        OAuthClient.AccessTokenResponse response = oauth.doGrantAccessTokenRequest("secret", "test-user@localhost", "password");

        assertEquals(200, response.getStatusCode());

        AccessToken accessToken = oauth.verifyToken(response.getAccessToken());
        RefreshToken refreshToken = oauth.verifyRefreshToken(response.getRefreshToken());

        events.expectLogin()
                .client("resource-owner")
                .session(accessToken.getSessionState())
                .detail(Details.AUTH_METHOD, "oauth_credentials")
                .detail(Details.RESPONSE_TYPE, "token")
                .detail(Details.TOKEN_ID, accessToken.getId())
                .detail(Details.REFRESH_TOKEN_ID, refreshToken.getId())
                .removeDetail(Details.CODE_ID)
                .removeDetail(Details.REDIRECT_URI)
                .assertEvent();

        HttpResponse logoutResponse = oauth.doLogout(null, accessToken.getSessionState());
        assertEquals(200, logoutResponse.getStatusLine().getStatusCode());
        events.expectLogout(accessToken.getSessionState()).removeDetail(Details.REDIRECT_URI).assertEvent();

        logoutResponse = oauth.doLogout(null, accessToken.getSessionState());
        assertEquals(200, logoutResponse.getStatusLine().getStatusCode());
        events.expectLogout(accessToken.getSessionState()).user((String) null).removeDetail(Details.REDIRECT_URI).error(Errors.USER_SESSION_NOT_FOUND).assertEvent();

        response = oauth.doRefreshTokenRequest(response.getRefreshToken(), "secret");
        assertEquals(400, response.getStatusCode());
        assertEquals("invalid_grant", response.getError());

        events.expectRefresh(refreshToken.getId(), refreshToken.getSessionState()).client("resource-owner")
                .removeDetail(Details.TOKEN_ID)
                .removeDetail(Details.UPDATED_REFRESH_TOKEN_ID)
                .error(Errors.INVALID_TOKEN).assertEvent();
    }

    @Test
    public void grantAccessTokenInvalidClientCredentials() throws Exception {
        oauth.clientId("resource-owner");

        OAuthClient.AccessTokenResponse response = oauth.doGrantAccessTokenRequest("invalid", "test-user@localhost", "password");

        assertEquals(400, response.getStatusCode());

        assertEquals("unauthorized_client", response.getError());

        events.expectLogin()
                .client("resource-owner")
                .session((String) null)
                .detail(Details.AUTH_METHOD, "oauth_credentials")
                .detail(Details.RESPONSE_TYPE, "token")
                .removeDetail(Details.CODE_ID)
                .removeDetail(Details.REDIRECT_URI)
                .error(Errors.INVALID_CLIENT_CREDENTIALS)
                .assertEvent();
    }

    @Test
    public void grantAccessTokenInvalidUserCredentials() throws Exception {
        oauth.clientId("resource-owner");

        OAuthClient.AccessTokenResponse response = oauth.doGrantAccessTokenRequest("secret", "test-user@localhost", "invalid");

        assertEquals(400, response.getStatusCode());

        assertEquals("invalid_grant", response.getError());

        events.expectLogin()
                .client("resource-owner")
                .session((String) null)
                .detail(Details.AUTH_METHOD, "oauth_credentials")
                .detail(Details.RESPONSE_TYPE, "token")
                .removeDetail(Details.CODE_ID)
                .removeDetail(Details.REDIRECT_URI)
                .error(Errors.INVALID_USER_CREDENTIALS)
                .assertEvent();
    }

}
