/*  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.pluto.container.driver;

import java.io.InputStream;
import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.pluto.container.om.portlet.impl.ConfigurationHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet container initializer that reads the configuration and adds the
 * portlet servlets for any portlets contained in the application.
 * 
 * @author Scott Nicklous
 * 
 */
// @HandlesTypes({PortletApplication.class, PortletConfiguration.class})
public class PortletContainerInitializer implements ServletContainerInitializer {

   private static final String WEB_XML     = "/WEB-INF/web.xml";
   private static final String PORTLET_XML = "/WEB-INF/portlet.xml";

   /** Logger. */
   private static final Logger LOG         = LoggerFactory
                                                 .getLogger(PortletContainerInitializer.class);
   private static boolean      isDebug     = LOG.isDebugEnabled();

   /*
    * (non-Javadoc)
    * 
    * @see javax.servlet.ServletContainerInitializer#onStartup(java.util.Set,
    * javax.servlet.ServletContext)
    */
   @Override
   public void onStartup(Set<Class<?>> classes, ServletContext ctx)
         throws ServletException {

      InputStream win = ctx.getResourceAsStream(WEB_XML);
      InputStream pin = ctx.getResourceAsStream(PORTLET_XML);

      if (isDebug) {
         StringBuilder txt = new StringBuilder(128);
         txt.append("§§§ ServletContainerInitializer. ctx path: ").append(
               ctx.getContextPath());
         txt.append(", servlet ctx name: ").append(ctx.getServletContextName());
         txt.append(", # portlet annotations: ").append(
               (classes != null) ? classes.size() : "null");
         txt.append(", found web.xml: ").append(win != null);
         txt.append(", found portlet.xml: ").append(pin != null);
         LOG.debug(txt.toString());
      }

      // If portlet configuration is available, parse it and launch the portlet
      // servlets
      if ((classes != null && classes.size() > 0) || pin != null) {

         try {
            ConfigurationHolder holder = new ConfigurationHolder();

            if (classes != null) {
               // digest any & all configuration annotations
            }

            if (pin != null) {
               // parse the portlet deployment descriptor
               holder.processPortletDD(pin);
            }

            if (win != null) {
               // parse the web app deployment descriptor
               holder.processWebDD(win);
            }

            ctx.setAttribute(ConfigurationHolder.ATTRIB_NAME, holder);
            
         } catch (Exception e) {
            StringBuilder txt = new StringBuilder(128);
            txt.append("Exception reading portlet application configuration");
            txt.append(", Servlet ctx name: ").append(
                  ctx.getServletContextName());
            txt.append(", Exception: ").append(e.getLocalizedMessage());
            LOG.error(txt.toString());
         }
      }

   }

}
