package org.keycloak.services;

/**
 * Information about the client connection
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public interface ClientConnection {
    String getRemoteAddr();
    String getRemoteHost();
    int getReportPort();
}
