/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.ui.struts2.core;

import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.CharSetUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.business.UserManager;
import org.apache.roller.weblogger.config.WebloggerConfig;
import org.apache.roller.weblogger.config.WebloggerRuntimeConfig;
import org.apache.roller.weblogger.pojos.User;
import org.apache.roller.weblogger.ui.core.security.CustomUserRegistry;
import org.apache.roller.weblogger.ui.struts2.util.UIAction;
import org.apache.roller.weblogger.util.MailUtil;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.validation.SkipValidation;


/**
 * Actions for registering a new user.
 */
public class Register extends UIAction implements ServletRequestAware {
    
    private static Log log = LogFactory.getLog(Register.class);
    
    public static String DEFAULT_ALLOWED_CHARS = "A-Za-z0-9";
    
    // this is a no-no, we should not need this
    private HttpServletRequest servletRequest = null;
    
    private boolean fromSS0 = false;
    private String activationStatus = null;
    
    private String activationCode = null;
    private ProfileBean bean = new ProfileBean();
    
    
    public Register() {
        this.pageTitle = "newUser.addNewUser";
    }
    
    
    // override default security, we do not require an authenticated user
    public boolean isUserRequired() {
        return false;
    }
    
    // override default security, we do not require an action weblog
    public boolean isWeblogRequired() {
        return false;
    }
    
    
    @SkipValidation
    public String execute() {
        
        if(!WebloggerRuntimeConfig.getBooleanProperty("users.registration.enabled")) {
            return "disabled";
        }
        
        // set some defaults
        getBean().setLocale(Locale.getDefault().toString());
        getBean().setTimeZone(TimeZone.getDefault().getID());
            
        try {
            // Let's see if there's any user-authentication available from Acegi
            // and retrieve custom user data to pre-populate form.
            boolean usingSSO = WebloggerConfig.getBooleanProperty("users.sso.enabled");
            if(usingSSO) {
                User fromSSO = CustomUserRegistry.getUserDetailsFromAuthentication();
                if(fromSSO != null) {
                    getBean().copyFrom(fromSSO);
                    setFromSS0(true);
                }
            }
            
        } catch (Exception ex) {
            log.error("Error reading SSO user data", ex);
            addError("error.editing.user", ex.toString());
        }
        
        return INPUT;
    }
    
    
    public String save() {
        
        if(!WebloggerRuntimeConfig.getBooleanProperty("users.registration.enabled")) {
            return "disabled";
        }
        
        myValidate();
        
        if (!hasActionErrors()) try {
            
            UserManager mgr = WebloggerFactory.getWeblogger().getUserManager();
            
            // copy form data into new user pojo
            User ud = new User();
            getBean().copyTo(ud); // doesn't copy password
            ud.setUserName(getBean().getUserName());
            ud.setDateCreated(new java.util.Date());
            ud.setEnabled(Boolean.TRUE);
            
            // If user set both password and passwordConfirm then reset password
            if (!StringUtils.isEmpty(getBean().getPasswordText()) && 
                    !StringUtils.isEmpty(getBean().getPasswordConfirm())) {
                ud.resetPassword(getBean().getPasswordText());
            }
            
            // are we using email activation?
            boolean activationEnabled = WebloggerRuntimeConfig.getBooleanProperty(
                    "user.account.activation.enabled");
            if (activationEnabled) {
                // User account will be enabled after the activation process
                ud.setEnabled(Boolean.FALSE);
                
                // Create & save the activation data
                String activationCode = UUID.randomUUID().toString();
                
                if (mgr.getUserByActivationCode(activationCode) != null) {
                    // In the *extremely* unlikely event that we generate an
                    // activation code that is already use, we'll retry 3 times.
                    int numOfRetries = 3;
                    if (numOfRetries < 1) numOfRetries = 1;
                    for (int i = 0; i < numOfRetries; i++) {
                        activationCode = UUID.randomUUID().toString();
                        if (mgr.getUserByActivationCode(activationCode) == null) {
                            break;
                        } else {
                            activationCode = null;
                        }
                    }
                    // In more unlikely event that three retries isn't enough
                    if (activationCode == null){
                        throw new WebloggerException("error.add.user.activationCodeInUse");
                    }
                }
                ud.setActivationCode(activationCode);
            }
            
            // save new user
            mgr.addUser(ud);
            WebloggerFactory.getWeblogger().flush();
            
            // now send activation email if necessary
            if (activationEnabled && ud.getActivationCode() != null) {
                try {
                    // send activation mail to the user
                    MailUtil.sendUserActivationEmail(ud);
                } catch (WebloggerException ex) {
                    log.error("Error sending activation email to - "+ud.getEmailAddress(), ex);
                }
                
                setActivationStatus("pending");
            }
             
            // Invalidate session, otherwise new user who was originally
            // authenticated via LDAP/SSO will remain logged in with
            // a but without a valid Roller role.
            getServletRequest().getSession().invalidate();
            
            // set a special page title
            setPageTitle("welcome.title");
            
            return SUCCESS;
            
        } catch (WebloggerException ex) {
            log.error("Error adding new user", ex);
            // TODO: i18n
            addError("Error adding new user");
        }
        
        return INPUT;
    }
    
    
    @SkipValidation
    public String activate() {
        
        try {
            UserManager mgr = WebloggerFactory.getWeblogger().getUserManager();
            
            if (getActivationCode() == null) {
                addError("error.activate.user.missingActivationCode");
            } else {
                User user = mgr.getUserByActivationCode(getActivationCode());
                
                if (user != null) {
                    // enable user account
                    user.setEnabled(Boolean.TRUE);
                    user.setActivationCode(null);
                    mgr.saveUser(user);
                    WebloggerFactory.getWeblogger().flush();
                    
                    setActivationStatus("active");
                    
                } else {
                    addError("error.activate.user.invalidActivationCode");
                }
            }
            
        } catch (WebloggerException e) {
            addError(e.getMessage());
            log.error("ERROR in activateUser", e);
        }
        
        if (hasActionErrors()) {
            setActivationStatus("error");
        }
        
        // set a special page title
        setPageTitle("welcome.title");
            
        return SUCCESS;
    }
    
    
    public void myValidate() {
        
        // if usingSSO, we don't want to error on empty password/username from HTML form.
        setFromSS0(false);
        boolean usingSSO = WebloggerConfig.getBooleanProperty("users.sso.enabled");
        if(usingSSO) {
            boolean storePassword = WebloggerConfig.getBooleanProperty("users.sso.passwords.saveInRollerDb");
            User fromSSO = CustomUserRegistry.getUserDetailsFromAuthentication();
            if(fromSSO != null) {
                String password = WebloggerConfig.getProperty("users.sso.passwords.defaultValue", "<unknown>");
                if(storePassword) {
                    password = fromSSO.getPassword();
                }
                getBean().setPasswordText(password);
                getBean().setPasswordConfirm(password);
                getBean().setUserName(fromSSO.getUserName());
                setFromSS0(true);
            }
        }
        
        String allowed = WebloggerConfig.getProperty("username.allowedChars");
        if(allowed == null || allowed.trim().length() == 0) {
            allowed = DEFAULT_ALLOWED_CHARS;
        }
        
        // check that username only contains safe characters
        String safe = CharSetUtils.keep(getBean().getUserName(), allowed);
        if (!safe.equals(getBean().getUserName()) ) {
            addError("error.add.user.badUserName");
        }
        
        // check that passwords match
        if(!getBean().getPasswordText().equals(getBean().getPasswordConfirm())) {
            addError("Register.error.passowordMismatch");
        }
        
        // check that username is not taken
        if(!StringUtils.isEmpty(getBean().getUserName())) try {
            UserManager mgr = WebloggerFactory.getWeblogger().getUserManager();
            if(mgr.getUserByUserName(getBean().getUserName(), null) != null) {
                addError("error.add.user.userNameInUse");
                // reset user name
                getBean().setUserName(null);
            }
        } catch (WebloggerException ex) {
            log.error("error checking for user", ex);
            // TODO: i18n
            addError("unexpected error");
        }
    }
    
    
    public HttpServletRequest getServletRequest() {
        return servletRequest;
    }

    public void setServletRequest(HttpServletRequest servletRequest) {
        this.servletRequest = servletRequest;
    }
    
    public ProfileBean getBean() {
        return bean;
    }

    public void setBean(ProfileBean bean) {
        this.bean = bean;
    }

    public boolean isFromSS0() {
        return fromSS0;
    }

    public void setFromSS0(boolean fromSS0) {
        this.fromSS0 = fromSS0;
    }

    public String getActivationStatus() {
        return activationStatus;
    }

    public void setActivationStatus(String activationStatus) {
        this.activationStatus = activationStatus;
    }

    public String getActivationCode() {
        return activationCode;
    }

    public void setActivationCode(String activationCode) {
        this.activationCode = activationCode;
    }
    
}
