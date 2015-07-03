package org.simbasecurity.core.chain.eid;

import org.simbasecurity.core.chain.ChainContext;
import org.simbasecurity.core.chain.session.LogoutCommand;
import org.simbasecurity.core.saml.SAMLService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class EIDLogoutCommand extends LogoutCommand {

    @Autowired SAMLService samlService;

    @Override
    protected void redirectToLogout(ChainContext context) {
        Map<String, String> parameters = new HashMap<String, String>();
        String logoutRequestId = context.getRequestSSOToken().getToken();
        context.redirectWithParameters(getSAMLLogoutRequest(logoutRequestId), parameters);
    }

    private String getSAMLLogoutRequest(String logoutRequestId) {
        try {
            return samlService.getLogoutRequestUrl(logoutRequestId, new Date());
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean postProcess(ChainContext context, Exception exception) {
        return false;
    }
}
