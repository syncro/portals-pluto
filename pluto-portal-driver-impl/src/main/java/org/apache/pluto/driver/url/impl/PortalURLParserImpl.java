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
package org.apache.pluto.driver.url.impl;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import javax.portlet.PortletMode;
import javax.portlet.WindowState;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pluto.driver.AttributeKeys;
import org.apache.pluto.driver.config.DriverConfiguration;
import org.apache.pluto.driver.services.portal.PublicRenderParameterMapper;
import org.apache.pluto.driver.url.PortalURL;
import org.apache.pluto.driver.url.PortalURLParameter;
import org.apache.pluto.driver.url.PortalURLParser;

/**
 * @version 1.0
 * @since Sep 30, 2004
 */
public class PortalURLParserImpl implements PortalURLParser {

   /** Logger. */
   private static final Logger LOG = LoggerFactory.getLogger(PortalURLParserImpl.class);

   /** The singleton parser instance. */
   private static final PortalURLParser PARSER = new PortalURLParserImpl();


   // Constants used for Encoding/Decoding ------------------------------------

   private static final String PREFIX = "__";
   private static final String DELIM = "_";
   private static final String PORTLET_ID = "pd";
   private static final String ACTION = "ac";
   private static final String RESOURCE = "rs";
   private static final String RESOURCE_ID = "ri";
   private static final String CACHE_LEVEL = "cl";
   private static final String RENDER_PARAM = "rp";
   private static final String PRIVATE_RENDER_PARAM = "pr";
   private static final String PUBLIC_RENDER_PARAM = "sp";
   private static final String WINDOW_STATE = "ws";
   private static final String PORTLET_MODE = "pm";
   private static final String VALUE_DELIM = "0x0";

   private static final String AJAX_ACTION = "aa";      // new for portlet spec 3
   private static final String PARTIAL_ACTION = "pa";   // new for portlet spec 3


   //This is a list of characters that need to be encoded  to be protected
   //The ? is necessary to protect URI's with a query portion that is being passed as a parameter
   private static final String[][] ENCODINGS = new String[][] {
      new String[] { "_",  "0x1" },
      new String[] { ".",  "0x2" },
      new String[] { "/",  "0x3" },
      new String[] { "\r", "0x4" },
      new String[] { "\n", "0x5" },
      new String[] { "<",  "0x6" },
      new String[] { ">",  "0x7" },
      new String[] { " ",  "0x8" },
      new String[] { "#",  "0x9" },
      new String[] { "?",  "0xa" },
      new String[] { "\\", "0xb" },
      new String[] { "%",  "0xc" },
   };

   // Constructor -------------------------------------------------------------

   /**
    * Private constructor that prevents external instantiation.
    */
   private PortalURLParserImpl() {
      // Do nothing.
   }

   /**
    * Returns the singleton parser instance.
    * @return the singleton parser instance.
    */
   public static PortalURLParser getParser() {
      return PARSER;
   }


   // Public Methods ----------------------------------------------------------

   /**
    * Parse a servlet request to a portal URL.
    * @param request  the servlet request to parse.
    * @return the portal URL.
    */
   public PortalURL parse(HttpServletRequest request) {

      if (LOG.isDebugEnabled()) {
         LOG.debug("Parsing URL: " + request.getRequestURI());
      }

      String contextPath = request.getContextPath();
      String servletName = request.getServletPath();

      String urlBase = request.getScheme()+"://" + request.getServerName() + ":" + request.getServerPort();
      // Construct portal URL using info retrieved from servlet request.
      PortalURL portalURL =  new RelativePortalURLImpl(urlBase, contextPath, servletName, this);

      // Support added for filter.  Should we seperate into a different impl?
      String pathInfo = request.getPathInfo();
      if (pathInfo == null) {
         if(servletName.contains(".jsp") && !servletName.endsWith(".jsp")) {
            int idx = servletName.indexOf(".jsp")+".jsp".length();
            pathInfo = servletName.substring(idx);
            servletName = servletName.substring(0, idx);
            portalURL = new RelativePortalURLImpl(urlBase, contextPath, servletName, this);
         } else {
            return portalURL;
         }
      }

      if (LOG.isDebugEnabled()) {
         LOG.debug("Parsing request pathInfo: " + pathInfo);
      }

      // Need to set the render path (= page) and PRP mapper before the rest of the URL is parsed
      StringBuffer renderPath = new StringBuffer();
      if (pathInfo.charAt(0) != '/') {
         renderPath.append('/');
      }

      int ind = pathInfo.indexOf("/" + PREFIX);
      if (ind < 0) {
         // there are no parameters to process
         renderPath.append(pathInfo);
         pathInfo = "";
      } else {
         // parameters, etc. remain to be processed
         renderPath.append(pathInfo.substring(0, ind));
         pathInfo = pathInfo.substring(ind);
      }

      portalURL.setRenderPath(renderPath.toString());
      if (LOG.isDebugEnabled()) {
         LOG.debug("Parse: renderPath: " + renderPath.toString() + ",  pathInfo: " + pathInfo);
      }

      ServletContext sc = request.getServletContext();
      DriverConfiguration dc = (DriverConfiguration) sc.getAttribute(AttributeKeys.DRIVER_CONFIG);
      PublicRenderParameterMapper prpm = dc.getPublicRenderParameterService()
            .getPRPMapper(renderPath.toString());
      portalURL.setPublicRenderParameterMapper(prpm);
      portalURL.setPortletIds(dc.getPageConfig(renderPath.toString()).getPortletIds());
      
      ArrayList<String> portletIds = new ArrayList<String>();

      // Tokenize the rest and process the tokens
      if (pathInfo.length() > 2) {
         String[] tokens = pathInfo.split("/" + PREFIX);
         for (String t : tokens) {
            
            // vals contains the component values of the token after the type
            String type, val;
            String[] vals;
            if (t.length() < 3) {
               LOG.warn("Token >>" + t + "<< is too short!! ");
               continue;
            } else {
               type = t.substring(0, 2);
               val = t.substring(2);
               vals = val.split(DELIM);
               for (int ii = 0; ii < vals.length; ii++) {
                  vals[ii] = decodeCharacters(vals[ii]);
               }
            }
            
            // If the first value is numeric, attempt to dereference the index to obtain the portlet ID.
            // The code assumes that the portlet ID table in the URL appears directly after the render path. 
            int index = -1;
            String pid = null;
            if ((vals[0].length() > 0) && vals[0].matches("\\d+")) {
               try {
                  index = Integer.parseInt(vals[0]);
                  if ((index >= 0) && (index < portletIds.size())) {
                     pid = portletIds.get(index);
                  }
               } catch (Exception e) {
                  LOG.error("error parsing URL pid reference token. Token: " + vals[0] + ", exception: " + e.toString());
               }
            }

            // Get the portlet IDs & reference numbers
            if (type.equals(PORTLET_ID)) {
               portletIds.add(Integer.parseInt(vals[1]), vals[0]);
               continue;
            } 
            
            // Cacheability definition: portalURL.setCacheability().
            if (type.equals(CACHE_LEVEL)) {
               portalURL.setCacheability(vals[0]);
               continue;
            }
            
            // ResourceID definition: portalURL.setResourceID().
            if (type.equals(RESOURCE_ID)) {
               portalURL.setResourceID(vals[0]);
               continue;
            }
            
            // Resource window definition: portalURL.setResourceWindow().
            if (type.equals(RESOURCE)) {
               portalURL.setResourceWindow(pid);
               continue;
            }
            
            // Action window definition: portalURL.setActionWindow().
            if (type.equals(ACTION)) {
               portalURL.setActionWindow(pid);
               continue;
            }
            
            // Action window definition: portalURL.setActionWindow().
            if (type.equals(AJAX_ACTION)) {
               portalURL.setAjaxActionWindow(pid);
               continue;
            }
            
            // Action window definition: portalURL.setActionWindow().
            if (type.equals(PARTIAL_ACTION)) {
               portalURL.setPartialActionWindow(pid);
               continue;
            }

            // Window state definition: portalURL.setWindowState().
            if (type.equals(WINDOW_STATE)) {
               portalURL.setWindowState(pid, new WindowState(vals[1]));
               continue;
            }
            
            // Portlet mode definition: portalURL.setPortletMode().
            if (type.equals(PORTLET_MODE)) {
               portalURL.setPortletMode(pid, new PortletMode(vals[1]));
               continue;
            }

          }
      }

      if (LOG.isDebugEnabled()) {
         LOG.debug("Found " + portletIds.size() + " IDs: " + Arrays.toString(portletIds.toArray()));
      }

      // Now parse the remaining portion of the URL, of any.
      StringTokenizer st = new StringTokenizer(pathInfo, "/", false);
      while (st.hasMoreTokens()) {

         String token = st.nextToken();

         // Portal URL parameter: portalURL.addParameter().
         if(token.startsWith(PREFIX + RENDER_PARAM)) {
            String value = null;
            if (st.hasMoreTokens()) {
               value = st.nextToken();
            }
            //set the
            PortalURLParameter param = decodeParameter(token, value);
            portalURL.addParameter(param);


         }
         else if (token.startsWith(PREFIX + PRIVATE_RENDER_PARAM)){
            String value = null;
            if (st.hasMoreTokens()) {
               value = st.nextToken();
            }
            PortalURLParameter param = decodePublicParameter(token, value);
            if( param != null )
            {
               //set private (Resource) parameter in portalURL
               portalURL.getPrivateRenderParameters().put(param.getName(), param.getValues());
            }
         }
         else if (token.startsWith(PREFIX + PUBLIC_RENDER_PARAM)){
            String value = null;
            if (st.hasMoreTokens()) {
               value = st.nextToken();
            }
            PortalURLParameter param = decodePublicParameter(token, value);
            if( param != null )
            {
               //set public parameter in portalURL
               portalURL.addPublicParameterCurrent(param.getName(), param.getValues());
            }
         }
      }        

      // Return the portal URL.
      return portalURL;
   }


   /**
    * Converts a portal URL to a URL string.
    * @param portalURL  the portal URL to convert.
    * @return a URL string representing the portal URL.
    */
   public String toString(PortalURL portalURL) {

      StringBuffer buffer = new StringBuffer();

      // Append the server URI and the servlet path.
      buffer.append(portalURL.getServletPath().startsWith("/")?"":"/")
      .append(portalURL.getServletPath());

      // Start the pathInfo with the path to the render URL (page).
      if (portalURL.getRenderPath() != null) {
         buffer.append(portalURL.getRenderPath());
      }
      
      // Add the portletIds with references
      ArrayList<String> pids = new ArrayList<String>();
      for (String pid : portalURL.getPortletIds()) {
         pids.add(pid);
         buffer.append("/").append(PREFIX).append(PORTLET_ID)
         .append(encodeCharacters(pid)).append(DELIM).append(String.valueOf(pids.indexOf(pid)));
      }
      
      //Append the resource window definition, if it exists.
      if (portalURL.getResourceWindow() != null) {
         int index = pids.indexOf(portalURL.getResourceWindow());
         if (index < 0) {
            LOG.warn("resource window not found in portlet ID list. PID = " + portalURL.getResourceWindow());
         } else {
            buffer.append("/");
            buffer.append(PREFIX).append(RESOURCE).append(String.valueOf(index));
         }
      }
      // Append the action window definition, if it exists.
      if (portalURL.getActionWindow() != null) {
         int index = pids.indexOf(portalURL.getActionWindow());
         if (index < 0) {
            LOG.warn("Action window not found in portlet ID list. PID = " + portalURL.getActionWindow());
         } else {
            buffer.append("/");
            buffer.append(PREFIX).append(ACTION).append(String.valueOf(index));
         }
      }

      if (portalURL.getResourceWindow() != null)
      {
         if (portalURL.getCacheability() != null)
         {
            buffer.append("/");
            buffer.append(PREFIX).append(CACHE_LEVEL).append(encodeCharacters(portalURL.getCacheability()));
         }
         if (portalURL.getResourceID() != null)
         {
            buffer.append("/");
            buffer.append(PREFIX).append(RESOURCE_ID).append(encodeCharacters(portalURL.getResourceID()));
         }
      }

      // Append portlet mode definitions.
      for (String pid : portalURL.getPortletModes().keySet()) {
         int index = pids.indexOf(pid);
         PortletMode pm = portalURL.getPortletMode(pid);
         if (index < 0) {
            LOG.warn("Window not found in portlet ID list. PID = " + pid + ", PM = " + pm.toString());
            LOG.warn("portlet IDs in map: " + Arrays.toString(pids.toArray()));
         } else {
            buffer.append("/").append(PREFIX).append(PORTLET_MODE)
            .append(String.valueOf(index)).append(DELIM).append(encodeCharacters(pm.toString()));
         }
      }

      // Append window state definitions.
      for (String pid : portalURL.getWindowStates().keySet()) {
         int index = pids.indexOf(pid);
         WindowState ws = portalURL.getWindowState(pid);
         if (index < 0) {
            LOG.warn("Window not found in portlet ID list. PID = " + pid + ", WS = " + ws.toString());
         } else {
            buffer.append("/").append(PREFIX).append(WINDOW_STATE)
            .append(String.valueOf(index)).append(DELIM).append(encodeCharacters(ws.toString()));
         }
      }

      // Append action and render parameters.
      StringBuffer query = new StringBuffer("?");
      boolean firstParam = true;
      for (Iterator it = portalURL.getParameters().iterator();
            it.hasNext(); ) {

         PortalURLParameter param = (PortalURLParameter) it.next();

         // Encode action params in the query appended at the end of the URL.
         if (portalURL.getActionWindow() != null
               && portalURL.getActionWindow().equals(param.getWindowId())
               || (portalURL.getResourceWindow() != null
               && portalURL.getResourceWindow().equals(param.getWindowId()))) {
            for (int i = 0; i < param.getValues().length; i++) {
               // FIX for PLUTO-247
               if ( firstParam ) {
                  firstParam = false;
               } else {
                  query.append("&");
               }
               query.append(encodeQueryParam(param.getName())).append("=")
               .append(encodeQueryParam(param.getValues()[i]));
            }
         }

         // Encode render params as a part of the URL.
         else if (param.getValues() != null
               && param.getValues().length > 0) {
            String valueString = encodeMultiValues(param.getValues());
            if (valueString.length() > 0) {
               buffer.append("/").append(
                     encodeControlParameter(RENDER_PARAM, param.getWindowId(),
                           param.getName()));
               buffer.append("/").append(valueString);
            }
         }
      }

      encode(buffer);

      if (portalURL.getResourceWindow() != null)
      {
         Map<String, String[]> privateParamList = portalURL.getPrivateRenderParameters();
         if (privateParamList!=null){
            for (Iterator iter = privateParamList.keySet().iterator();iter.hasNext();){
               String paramname = (String)iter.next();
               String[] tmp = privateParamList.get(paramname);
               String valueString = encodeMultiValues(tmp);
               if (valueString.length()>0){
                  buffer.append("/").append(encodePublicParamname(PRIVATE_RENDER_PARAM, paramname));
                  buffer.append("/").append(valueString);
               }
            }
         }
      }

      Map<String, String[]> publicParamList = portalURL.getPublicParameters();
      if (publicParamList!=null){
         for (Iterator iter = publicParamList.keySet().iterator();iter.hasNext();){
            String paramname = (String)iter.next();
            String[] tmp = publicParamList.get(paramname);
            String valueString = encodeMultiValues(tmp);
            if (valueString.length()>0){
               buffer.append("/").append(encodePublicParamname(PUBLIC_RENDER_PARAM, paramname));
               buffer.append("/").append(valueString);
            }
         }
      }

      // Construct the string representing the portal URL.
      // Fix for PLUTO-247 - check if query string contains parameters
      if ( query.length() > 1 ) {
         return buffer.append(query).toString();
      }

      // Construct the string representing the portal URL.
      return buffer.toString();
   }
   
   private static void encode(StringBuffer url){
      replaceChar(url,"|","%7C");
      replaceChar(url,"\"","%22");
   }
   private static void replaceChar(StringBuffer url, String character, String change){
      boolean contains = url.toString().contains(character);
      while (contains){
         int index = url.indexOf(character);
         url.deleteCharAt(index);
         url.insert(index, change, 0, 3);
         contains = url.toString().contains(character);
      }
   }

   private String encodeQueryParam(String param) {
      try {
         return URLEncoder.encode(param, "UTF-8");
      }
      catch (UnsupportedEncodingException e) {
         // If this happens, we've got bigger problems.
         throw new RuntimeException(e);
      }
   }

   // Private Encoding/Decoding Methods ---------------------------------------

   /**
    * Encode a control parameter.
    * @param type  the type of the control parameter, which may be:
    *              portlet mode, window state, or render parameter.
    * @param windowId  the portlet window ID.
    * @param name  the name to encode.
    */
   private String encodeControlParameter(String type,
         String windowId,
         String name) {
      StringBuffer buffer = new StringBuffer();
      buffer.append(PREFIX).append(type)
      .append(encodeCharacters(windowId))
      .append(DELIM).append(name);
      return buffer.toString();
   }

   private String encodePublicParamname(String type, String name){
      StringBuffer buffer = new StringBuffer();
      buffer.append(PREFIX).append(type)
      .append(DELIM).append(name);
      return buffer.toString();
   }

   /**
    * Encode a string array containing multiple values into a single string.
    * This method is used to encode multiple render parameter values.
    * @param values  the string array to encode.
    * @return a single string containing all the values.
    */
   private String encodeMultiValues(String[] values) {
      StringBuffer buffer = new StringBuffer();
      for (int i = 0; i < values.length; i++) {
         // Do not operate on the array reference
         String currentValue = values[i];
         try {
            if (currentValue != null)
               currentValue = URLEncoder.encode(values[i], "UTF-8");
         } catch (UnsupportedEncodingException e) {
            LOG.warn(e.getMessage(),e);
         }
         buffer.append(currentValue != null ? currentValue : "");
         if (i + 1 < values.length) {
            buffer.append(VALUE_DELIM);
         }
      }
      return encodeCharacters(buffer.toString());
   }

   /**
    * Encode special characters contained in the string value.
    * @param string  the string value to encode.
    * @return the encoded string.
    */
   private String encodeCharacters(String string) {
      for (int i = 0; i < ENCODINGS.length; i++) {
         string = string.replace(ENCODINGS[i][0], ENCODINGS[i][1]);
      }
      return string;
   }


   /**
    * Decode a control parameter.
    * @param control  the control parameter to decode.
    * @return values  a pair of decoded values.
    */
   private String[] decodeControlParameter(String control) {
      String[] valuePair = new String[2];
      control = control.substring((PREFIX + PORTLET_ID).length());
      int index = control.indexOf(DELIM);
      if (index >= 0) {
         valuePair[0] = control.substring(0, index);
         valuePair[0] = decodeCharacters(valuePair[0]);
         if (index + 1 <= control.length()) {
            valuePair[1] = control.substring(index + 1);
            valuePair[1] = decodeCharacters(valuePair[1]);
         } else {
            valuePair[1] = "";
         }
      } else {
         valuePair[0] = decodeCharacters(control);
      }
      return valuePair;
   }

   /**
    * Decode a name-value pair into a portal URL parameter.
    * @param name  the parameter name.
    * @param value  the parameter value.
    * @return the decoded portal URL parameter.
    */
   private PortalURLParameter decodeParameter(String name, String value) {

      if (LOG.isDebugEnabled()) {
         LOG.debug("Decoding parameter: name=" + name
               + ", value=" + value);
      }

      // Decode the name into window ID and parameter name.
      String noPrefix = name.substring((PREFIX + PORTLET_ID).length());
      String windowId = noPrefix.substring(0, noPrefix.indexOf(DELIM));
      String paramName = noPrefix.substring(noPrefix.indexOf(DELIM) + 1);

      // Decode special characters in window ID and parameter value.
      windowId = decodeCharacters(windowId);
      if (value != null) {
         value = decodeCharacters(value);
      }

      // Split multiple values into a value array.
      String[] paramValues = value.split(VALUE_DELIM);
      for (int i = 0; i < paramValues.length;i++){
         try {
            paramValues[i] = URLDecoder.decode(paramValues[i], "UTF-8");
         } catch (UnsupportedEncodingException e) {
            LOG.warn(e.getMessage(),e);
         }
      }
      // Construct portal URL parameter and return.
      return new PortalURLParameter(windowId, paramName, paramValues);
   }

   private PortalURLParameter decodePublicParameter(String name, String value) {

      if (LOG.isDebugEnabled()) {
         LOG.debug("Decoding parameter: name=" + name
               + ", value=" + value);
      }

      //    	// Decode the name into window ID and parameter name.
      String noPrefix = name.substring((PREFIX + PORTLET_ID).length());
      String paramName = noPrefix.substring(noPrefix.indexOf(DELIM) + 1);

      // Decode special characters in parameter value.

      if (value != null) {
         value = decodeCharacters(value);
      }

      // Split multiple values into a value array.
      String[] paramValues = value.split(VALUE_DELIM);
      for (int i = 0; i < paramValues.length;i++){
         try {
            paramValues[i] = URLDecoder.decode(paramValues[i], "UTF-8");
         } catch (UnsupportedEncodingException e) {
            LOG.warn(e.getMessage(),e);
         }
      }

      // Construct portal URL parameter and return.
      return new PortalURLParameter(null, paramName, paramValues);
   }

   /**
    * Decode special characters contained in the string value.
    * @param string  the string value to decode.
    * @return the decoded string.
    */
   private String decodeCharacters(String string) {
      for (int i = 0; i < ENCODINGS.length; i++) {
         string = string.replace(ENCODINGS[i][1], ENCODINGS[i][0]);
      }
      return string;
   }

}

