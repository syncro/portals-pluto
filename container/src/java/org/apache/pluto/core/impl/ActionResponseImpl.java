/*
 * Copyright 2003,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* 

 */

package org.apache.pluto.core.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.portlet.ActionResponse;
import javax.portlet.PortletMode;
import javax.portlet.PortletModeException;
import javax.portlet.WindowState;
import javax.portlet.WindowStateException;

import org.apache.pluto.core.InternalActionResponse;
import org.apache.pluto.om.window.PortletWindow;
import org.apache.pluto.services.information.DynamicInformationProvider;
import org.apache.pluto.services.information.InformationProviderAccess;
import org.apache.pluto.services.information.ResourceURLProvider;
import org.apache.pluto.util.StringUtils;

public class ActionResponseImpl extends PortletResponseImpl
implements ActionResponse, InternalActionResponse {

    /**
     * Is it still allowed to invoke the method sendRedirect() ?
     */
    boolean redirectAllowed = true;

    private boolean redirected;
    private String redirectLocation;

    private Map renderParameters = new HashMap();
    private WindowState windowState = null;
    private PortletMode portletMode = null;

    private DynamicInformationProvider provider;


    public ActionResponseImpl(PortletWindow portletWindow,
                              javax.servlet.http.HttpServletRequest servletRequest,
                              javax.servlet.http.HttpServletResponse servletResponse)
    {
        super(portletWindow, servletRequest, servletResponse);

        provider = InformationProviderAccess.getDynamicProvider(getHttpServletRequest());

    }

    // javax.portlet.ActionResponse ---------------------------------------------------------------
    public void setWindowState (WindowState windowState) throws WindowStateException
    {
        if (redirected) {
            throw new IllegalStateException("it is not allowed to invoke setWindowState after sendRedirect has been called");
        }

        if (provider.isWindowStateAllowed(windowState)) {
            this.windowState = windowState;
        } else {
            throw new WindowStateException("Can't set this WindowState",windowState);
        }
        redirectAllowed = false;
    }
    
    public void setPortletMode (PortletMode portletMode) throws PortletModeException
    {
        if (redirected) {
            throw new IllegalStateException("it is not allowed to invoke setPortletMode after sendRedirect has been called");
        }

        // check if portal supports portlet mode
        boolean supported = provider.isPortletModeAllowed(portletMode);

        // check if portlet supports portlet mode as well
        if (supported)
        {
            supported = PortletModeHelper.isPortletModeAllowedByPortlet(getInternalPortletWindow(), portletMode);
        }

        // if porlet mode is allowed
        if (supported) {
            this.portletMode = portletMode;
        } else
            throw new PortletModeException("Can't set this PortletMode",portletMode);

        redirectAllowed = false;

    }

    public void sendRedirect(String location) throws java.io.IOException
    {
        if (redirectAllowed) {
            if (location != null) {
                javax.servlet.http.HttpServletResponse redirectResponse = _getHttpServletResponse();
                while (redirectResponse instanceof javax.servlet.http.HttpServletResponseWrapper) {
                    redirectResponse = (javax.servlet.http.HttpServletResponse)
                                       ((javax.servlet.http.HttpServletResponseWrapper)redirectResponse).getResponse();
                }
                ResourceURLProvider provider = InformationProviderAccess.getDynamicProvider(getHttpServletRequest()).getResourceURLProvider(getInternalPortletWindow());
                if (location.indexOf("://") != -1) {
                    provider.setAbsoluteURL(location);
                } else if (location.startsWith("/")) {
                    provider.setFullPath(location);
                } else {
                    throw new IllegalArgumentException("Only absolute and full path URLs are allowed.  The relative path '" + location+"' is not valid.");
                }
                location = redirectResponse.encodeRedirectURL(provider.toString());
                //redirectResponse.sendRedirect(location);
                redirectLocation = location;
                redirected = true;
            }
        } else
            throw new java.lang.IllegalStateException("Can't invoke sendRedirect() after certain methods have been called");

    }
    
    public void setRenderParameters(Map parameters)
    {
        if (redirected) {
            throw new IllegalStateException("Can't invoke setRenderParameters() after sendRedirect() has been called");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("Render parameters must not be null.");
        }
        for (Iterator iter = parameters.entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = (Map.Entry)iter.next();
            if (!(entry.getKey() instanceof String)) {
                throw new IllegalArgumentException("Key must not be null and of type java.lang.String.");
            }
            if (!(entry.getValue() instanceof String[])) {
                throw new IllegalArgumentException("Value must not be null and of type java.lang.String[].");
            }
        }

        renderParameters = StringUtils.copyParameters(parameters);

        redirectAllowed = false;
    }
    
    public void setRenderParameter(String key, String value)
    {
        if (redirected) {
            throw new IllegalStateException("Can't invoke setRenderParameter() after sendRedirect() has been called");
        }

        if ((key == null) || (value == null)) {
            throw new IllegalArgumentException("Render parameter key or value must not be null.");
        }

        renderParameters.put(key, new String[] {value});

        redirectAllowed = false;
    }
    
    public void setRenderParameter(String key, String[] values)
    {
        if (redirected) {
            throw new IllegalStateException("Can't invoke setRenderParameter() after sendRedirect() has been called");
        }

        if (key == null || values == null || values.length == 0) {
            throw new IllegalArgumentException("Render parameter key or value must not be null or values be an empty array.");
        }

        renderParameters.put(key, StringUtils.copy(values));

        redirectAllowed = false;
    }
    // --------------------------------------------------------------------------------------------
    
    // org.apache.pluto.core.InternalActionResponse implementation --------------------------------
    public Map getRenderParameters()
    {
        return renderParameters;
    }

    public PortletMode getChangedPortletMode()
    {
        return this.portletMode;
    }

    public WindowState getChangedWindowState()
    {
        return this.windowState;
    }

    public String getRedirectLocation() {
        return redirectLocation;
    }
    // --------------------------------------------------------------------------------------------
}