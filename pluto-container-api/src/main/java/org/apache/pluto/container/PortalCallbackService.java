/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pluto.container;

import javax.servlet.http.HttpServletRequest;

import org.apache.pluto.container.om.portlet.PortletApplicationDefinition;

/**
 * The callback service interface defining callback methods that will be invoked
 * by the portlet container when necessary.
 * @version 1.0
 * @since Sep 21, 2004
 */
public interface PortalCallbackService {

    /**
     * Returns a URL provider used to constuct a URL to the given portlet.
     * @param request  the servlet request.
     * @param portletWindow  the portlet window.
     * @return the URL provider used to construct a URL to the given portlet.
     */
    public PortletURLProvider getPortletURLProvider(HttpServletRequest request,
                                                    PortletWindow portletWindow);

    /**
     * Returns a URL provider used to construct a URL to a resource in the web
     * application.
     * @param request  the servlet request.
     * @param portletWindow  the portlet window.
     * @return the URL provider used to construct a URL to a resource.
     */
    public ResourceURLProvider getResourceURLProvider(HttpServletRequest request,
                                                      PortletWindow portletWindow);

    public EventProvider getEventProvider(HttpServletRequest request,
			PortletWindow portletWindow);
    
    /**
     * Returns the FilterManager, this is used to process the filter.
     * @return FilterManager
     */
    public FilterManager getFilterManager(PortletApplicationDefinition portletAppDD, String portletName, String lifeCycle); 
    
    /**
     * Returns the PortletURLListener which calls the Filter for the URL.
     * @return
     */
    public PortletURLListener getPortletURLListener();

}