/*
 * Helma License Notice
 *
 * The contents of this file are subject to the Helma License
 * Version 2.0 (the "License"). You may not use this file except in
 * compliance with the License. A copy of the License is available at
 * http://adele.helma.org/download/helma/license.txt
 *
 * Copyright 1998-2003 Helma Software. All Rights Reserved.
 *
 * $RCSfile$
 * $Author$
 * $Revision$
 * $Date$
 */

package helma.framework.core;

import helma.objectmodel.INode;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;

/**
 * The SessionBean wraps a <code>Session</code> object and
 * exposes it to the scripting framework.
 */
public class SessionBean implements Serializable {
    // the wrapped session object
    Session session;

    /**
     * Creates a new SessionBean around a Session object.
     *
     * @param session ...
     */
    public SessionBean(Session session) {
        this.session = session;
    }

    /**
     *
     *
     * @return ...
     */
    public String toString() {
        return session.toString();
    }

    /**
     * Attempts to log in a user with the given username/password credentials.
     * If username and password match, the user node is associated with the session
     * and bound to the session.user property.
     *
     * @param username the username
     * @param password the password
     *
     * @return true if the user exists and the password matches the user's password property.
     */
    public boolean login(String username, String password) {
        boolean success = session.getApp().loginSession(username, password, session);

        return success;
    }

    /**
     * Directly associates the session with a user object without requiring
     * a username/password pair. This is for applications that use their own
     * authentication mechanism.
     *
     * @param userNode the HopObject node representing the user.
     */
    public void login(INode userNode) {
        session.login(userNode);
    }

    /**
     * Disassociate this session from any user object it may have been associated with.
     */
    public void logout() {
        session.getApp().logoutSession(session);
    }

    /**
     * Touching the session marks it as active, avoiding session timeout.
     * Usually, sessions are touched when the user associated with it sends
     * a request. This method may be used to artificially keep a session alive.
     */
    public void touch() {
        session.touch();
    }

    /**
     * Returns the time this session was last touched.
     *
     * @return ...
     */
    public Date lastActive() {
        return new Date(session.lastTouched());
    }

    /**
     * Returns the time this session was created.
     *
     * @return ...
     */
    public Date onSince() {
        return new Date(session.onSince());
    }

    // property-related methods:

    /**
     * Get the cache/data node for this session. This object may be used
     * to store transient per-session data. It is reflected to the scripting
     * environment as session.data.
     */
    public INode getdata() {
        return session.getCacheNode();
    }

    /**
     * Gets the user object for this session. This method returns null unless
     * one of the session.login methods was previously invoked.
     *
     * @return ...
     */
    public INode getuser() {
        return session.getUserNode();
    }

    /**
     * Returns the unique identifier for a session object (session cookie).
     *
     * @return ...
     */
    public String get_id() {
        return session.getSessionID();
    }

    /**
     * Returns the unique identifier for a session object (session cookie).
     *
     * @return ...
     */
    public String getcookie() {
        return session.getSessionID();
    }

    /**
     * Returns the time this session was last touched.
     *
     * @return ...
     */
    public Date getlastActive() {
        return new Date(session.lastTouched());
    }

    /**
     * Returns a date object representing the time a user's session was started.
     *
     * @return ...
     */
    public Date getonSince() {
        return new Date(session.onSince());
    }

    /**
     * Gets the date at which the session was created or a login or
     * logout was performed the last time.
     *
     * @return ...
     */
    public Date getLastModified() {
        return new Date(session.lastModified());
    }

    /**
     * Sets the date at which the session was created or a login or
     * logout was performed the last time.
     *
     * @param date ...
     */
    public void setLastModified(Date date) {
        session.setLastModified(date);
    }
}
