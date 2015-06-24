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

/* Portierung von helma.asp.AspClient auf Servlets */
/* Author: Raphael Spannocchi Datum: 27.11.1998 */

package helma.servlet;

import helma.framework.*;
import helma.util.*;
import java.io.*;
import java.net.URLDecoder;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 * This is an abstract Hop servlet adapter. This class communicates with hop applications
 * via RMI. Subclasses are either one servlet per app, or one servlet that handles multiple apps
 */
public abstract class AbstractServletClient extends HttpServlet {
    static final byte HTTP_GET = 0;
    static final byte HTTP_POST = 1;

    // host on which Helma app is running
    String host = null;

    // port of Helma RMI server
    int port = 0;

    // RMI url of Helma app
    String hopUrl;

    // limit to HTTP uploads in kB
    int uploadLimit = 1024;

    // cookie domain to use
    String cookieDomain;

    // default encoding for requests
    String defaultEncoding;

    // allow caching of responses
    boolean caching;

    // enable debug output
    boolean debug;

    /**
     *
     *
     * @param init ...
     *
     * @throws ServletException ...
     */
    public void init(ServletConfig init) throws ServletException {
        super.init(init);

        // get max size for file uploads
        String upstr = init.getInitParameter("uploadLimit");

        uploadLimit = (upstr == null) ? 1024 : Integer.parseInt(upstr);

        // get cookie domain
        cookieDomain = init.getInitParameter("cookieDomain");

        if (cookieDomain != null) {
            cookieDomain = cookieDomain.toLowerCase();
        }

        // get default encoding
        defaultEncoding = init.getInitParameter("charset");
        debug = ("true".equalsIgnoreCase(init.getInitParameter("debug")));
        caching = !("false".equalsIgnoreCase(init.getInitParameter("caching")));
    }

    abstract ResponseTrans execute(RequestTrans req) throws Exception;

    /**
     *
     *
     * @param request ...
     * @param response ...
     *
     * @throws ServletException ...
     * @throws IOException ...
     */
    public void doGet(HttpServletRequest request, HttpServletResponse response)
               throws ServletException, IOException {
        execute(request, response, HTTP_GET);
    }

    /**
     *
     *
     * @param request ...
     * @param response ...
     *
     * @throws ServletException ...
     * @throws IOException ...
     */
    public void doPost(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
        execute(request, response, HTTP_POST);
    }

    protected void execute(HttpServletRequest request, HttpServletResponse response,
                           byte method) {
        RequestTrans reqtrans = new RequestTrans(request, response, null);
        // get app and path from original request path
        // String pathInfo = request.getPathInfo ();
        // String appID = getAppID (pathInfo);
        // reqtrans.path = getRequestPath (pathInfo);
        try {
            // read and set http parameters
            Map parameters = parseParameters(request);

            for (Iterator i = parameters.entrySet().iterator(); i.hasNext();) {
                Map.Entry entry = (Map.Entry) i.next();
                String key = (String) entry.getKey();
                String[] values = (String[]) entry.getValue();

                if ((values != null) && (values.length > 0)) {
                    reqtrans.set(key, values[0]); // set to single string value

                    if (values.length > 1) {
                        reqtrans.set(key + "_array", values); // set string array
                    }
                }
            }

            // check for MIME file uploads
            String contentType = request.getContentType();

            if ((contentType != null) &&
                    (contentType.indexOf("multipart/form-data") == 0)) {
                // File Upload
                try {
                    FileUpload upload = getUpload(request);

                    if (upload != null) {
                        Hashtable parts = upload.getParts();

                        for (Enumeration e = parts.keys(); e.hasMoreElements();) {
                            String nextKey = (String) e.nextElement();
                            Object nextPart = parts.get(nextKey);

                            reqtrans.set(nextKey, nextPart);
                        }
                    }
                } catch (Exception upx) {
                    sendError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                              "Sorry, upload size exceeds limit of " + uploadLimit +
                              "kB.");

                    return;
                }
            }

            // read cookies
            Cookie[] reqCookies = request.getCookies();

            if (reqCookies != null) {
                for (int i = 0; i < reqCookies.length; i++)
                    try {
                        // get Cookies
                        String nextKey = reqCookies[i].getName();
                        String nextPart = reqCookies[i].getValue();

                        if ("HopSession".equals(nextKey)) {
                            reqtrans.session = nextPart;
                        } else {
                            reqtrans.set(nextKey, nextPart);
                        }
                    } catch (Exception badCookie) {
                    }
            }

            // do standard HTTP variables
            String host = request.getHeader("Host");

            if (host != null) {
                host = host.toLowerCase();
                reqtrans.set("http_host", host);
            }

            String referer = request.getHeader("Referer");

            if (referer != null) {
                reqtrans.set("http_referer", referer);
            }

            try {
                long ifModifiedSince = request.getDateHeader("If-Modified-Since");

                if (ifModifiedSince > -1) {
                    reqtrans.setIfModifiedSince(ifModifiedSince);
                }
            } catch (IllegalArgumentException ignore) {
            }

            String ifNoneMatch = request.getHeader("If-None-Match");

            if (ifNoneMatch != null) {
                reqtrans.setETags(ifNoneMatch);
            }

            String remotehost = request.getRemoteAddr();

            if (remotehost != null) {
                reqtrans.set("http_remotehost", remotehost);
            }

            // get the cookie domain to use for this response, if any.
            String resCookieDomain = cookieDomain;

            if (resCookieDomain != null) {
                // check if cookieDomain is valid for this response.
                // (note: cookieDomain is guaranteed to be lower case)
                if ((host != null) && (host.toLowerCase().indexOf(cookieDomain) == -1)) {
                    resCookieDomain = null;
                }
            }

            // check if session cookie is present and valid, creating it if not.
            checkSessionCookie(request, response, reqtrans, resCookieDomain);

            String browser = request.getHeader("User-Agent");

            if (browser != null) {
                reqtrans.set("http_browser", browser);
            }

            String authorization = request.getHeader("authorization");

            if (authorization != null) {
                reqtrans.set("authorization", authorization);
            }

            // response.setHeader ("Server", "Helma/"+helma.main.Server.version);
            reqtrans.path = getPathInfo(request);

            ResponseTrans restrans = execute(reqtrans);

            // set cookies
            if (restrans.countCookies() > 0) {
                CookieTrans[] resCookies = restrans.getCookies();

                for (int i = 0; i < resCookies.length; i++)
                    try {
                        Cookie c = resCookies[i].getCookie("/", resCookieDomain);

                        response.addCookie(c);
                    } catch (Exception ignore) {
                    }
            }

            // write response
            writeResponse(request, response, restrans);
        } catch (Exception x) {
            try {
                if (debug) {
                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                              "Error in request handler:" + x);
                    x.printStackTrace();
                } else {
                    sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                              "The server encountered an error while processing your request. " +
                              "Please check back later.");
                }

                log("Exception in execute: " + x);
            } catch (IOException io_e) {
                log("Exception in sendError: " + io_e);
            }
        }
    }

    void writeResponse(HttpServletRequest req, HttpServletResponse res,
                       ResponseTrans hopres) {
        if (hopres.getETag() != null) {
            res.setHeader("ETag", hopres.getETag());
        }

        if (hopres.getRedirect() != null) {
            sendRedirect(req, res, hopres.getRedirect());
        } else if (hopres.getNotModified()) {
            res.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        } else {
            if (!hopres.cache || !caching) {
                // Disable caching of response.
                // for HTTP 1.0
                res.setDateHeader("Expires", System.currentTimeMillis() - 10000);
                res.setHeader("Pragma", "no-cache");

                // for HTTP 1.1
                res.setHeader("Cache-Control",
                              "no-cache, no-store, must-revalidate, max-age=0");
            }

            if (hopres.realm != null) {
                res.setHeader("WWW-Authenticate", "Basic realm=\"" + hopres.realm + "\"");
            }

            if (hopres.status > 0) {
                res.setStatus(hopres.status);
            }

            // set last-modified header to now
            long modified = hopres.getLastModified();

            if (modified > -1) {
                res.setDateHeader("Last-Modified", System.currentTimeMillis());
            }

            // if we don't know which charset to use for parsing HTTP params,
            // take the one from the response. This usually works because
            // browsers send parrameters in the same encoding as the page
            // containing the form has. Problem is we can do this only per servlet,
            // not per session or even per page, which would produce too much overhead
            if (defaultEncoding == null) {
                defaultEncoding = hopres.charset;
            }

            res.setContentLength(hopres.getContentLength());
            res.setContentType(hopres.getContentType());

            try {
                OutputStream out = res.getOutputStream();

                out.write(hopres.getContent());
                out.flush();
            } catch (Exception io_e) {
                log("Exception in writeResponse: " + io_e);
            }
        }
    }

    void sendError(HttpServletResponse response, int code, String message)
            throws IOException {
        response.reset();
        response.setStatus(code);
        response.setContentType("text/html");

        Writer writer = response.getWriter();

        writer.write(message);
        writer.flush();
    }

    void sendRedirect(HttpServletRequest req, HttpServletResponse res, String url) {
        String location = url;

        if (url.indexOf("://") == -1) {
            // need to transform a relative URL into an absolute one
            String scheme = req.getScheme();
            StringBuffer loc = new StringBuffer(scheme);

            loc.append("://");
            loc.append(req.getServerName());

            int p = req.getServerPort();

            // check if we need to include server port
            if ((p > 0) &&
                    (("http".equals(scheme) && (p != 80)) ||
                    ("https".equals(scheme) && (p != 443)))) {
                loc.append(":");
                loc.append(p);
            }

            if (!url.startsWith("/")) {
                String requri = req.getRequestURI();
                int lastSlash = requri.lastIndexOf("/");

                if (lastSlash == (requri.length() - 1)) {
                    loc.append(requri);
                } else if (lastSlash > -1) {
                    loc.append(requri.substring(0, lastSlash + 1));
                } else {
                    loc.append("/");
                }
            }

            loc.append(url);
            location = loc.toString();
        }

        // send status code 303 for HTTP 1.1, 302 otherwise
        if (isOneDotOne(req.getProtocol())) {
            res.setStatus(HttpServletResponse.SC_SEE_OTHER);
        } else {
            res.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
        }

        res.setContentType("text/html");
        res.setHeader("Location", location);
    }

    FileUpload getUpload(HttpServletRequest request) throws Exception {
        int contentLength = request.getContentLength();
        BufferedInputStream in = new BufferedInputStream(request.getInputStream());

        if (contentLength > (uploadLimit * 1024)) {
            throw new RuntimeException("Upload exceeds limit of " + uploadLimit + " kb.");
        }

        String contentType = request.getContentType();
        FileUpload upload = new FileUpload(uploadLimit);

        upload.load(in, contentType, contentLength);

        return upload;
    }

    Object getUploadPart(FileUpload upload, String name) {
        return upload.getParts().get(name);
    }

    /**
     *  Check if the session cookie is set and valid for this request.
     *  If not, create a new one.
     */
    private void checkSessionCookie(HttpServletRequest request, HttpServletResponse response,
                        RequestTrans reqtrans, String resCookieDomain) {
        // check if we need to create a session id. also handle the
        // case that the session id doesn't match the remote host address
        StringBuffer b = new StringBuffer();
        addIPAddress(b, request.getRemoteAddr());
        addIPAddress(b, request.getHeader("X-Forwarded-For"));
        addIPAddress(b, request.getHeader("Client-ip"));
        if ((reqtrans.session == null) || !reqtrans.session.startsWith(b.toString())) {
            b.append (Long.toString(Math.round(Math.random() * Long.MAX_VALUE) -
                        System.currentTimeMillis(), 36));

            reqtrans.session = b.toString();
            Cookie c = new Cookie("HopSession", reqtrans.session);

            c.setPath("/");

            if (resCookieDomain != null) {
                c.setDomain(resCookieDomain);
            }

            response.addCookie(c);
        }
    }

    /**
     *  Adds an the 3 most significant bytes of an IP address to the
     *  session cookie id.
     */
    private void addIPAddress(StringBuffer b, String addr) {
        if (addr != null) {
            int cut = addr.lastIndexOf(".");
            if (cut == -1) {
                cut = addr.lastIndexOf(":");
            }
            if (cut > -1) {
                b.append(addr.substring(0, cut+1));
            }
        }
    }


    /**
     * Put name value pair in map.
     *
     * @param b the character value byte
     *
     * Put name and value pair in map.  When name already exist, add value
     * to array of values.
     */
    private static void putMapEntry(Map map, String name, String value) {
        String[] newValues = null;
        String[] oldValues = (String[]) map.get(name);

        if (oldValues == null) {
            newValues = new String[1];
            newValues[0] = value;
        } else {
            newValues = new String[oldValues.length + 1];
            System.arraycopy(oldValues, 0, newValues, 0, oldValues.length);
            newValues[oldValues.length] = value;
        }

        map.put(name, newValues);
    }

    protected Map parseParameters(HttpServletRequest request) {
        String encoding = request.getCharacterEncoding();

        if (encoding == null) {
            // no encoding from request, use standard one
            encoding = defaultEncoding;
        }

        if (encoding == null) {
            encoding = "ISO-8859-1";
        }

        HashMap parameters = new HashMap();

        // Parse any query string parameters from the request
        String queryString = request.getQueryString();
        if (queryString != null) {
            try {
                parseParameters(parameters, queryString.getBytes(), encoding);
            } catch (Exception e) {
                System.err.println("Error parsing query string: "+e);
            }
        }

        // Parse any posted parameters in the input stream
        String contentType = request.getContentType();
        if (contentType != null) {
            final String[] parts = contentType.split(";");
            contentType = parts[0];
        }
        if ("POST".equals(request.getMethod()) &&
                "application/x-www-form-urlencoded".equals(contentType)) {
            try {
                int max = request.getContentLength();
                int len = 0;
                byte[] buf = new byte[max];
                ServletInputStream is = request.getInputStream();

                while (len < max) {
                    int next = is.read(buf, len, max - len);

                    if (next < 0) {
                        break;
                    }

                    len += next;
                }

                // is.close();
                parseParameters(parameters, buf, encoding);
            } catch (IllegalArgumentException e) {
            } catch (IOException e) {
            }
        }

        return parameters;
    }

    /**
     * Append request parameters from the specified String to the specified
     * Map.  It is presumed that the specified Map is not accessed from any
     * other thread, so no synchronization is performed.
     * <p>
     * <strong>IMPLEMENTATION NOTE</strong>:  URL decoding is performed
     * individually on the parsed name and value elements, rather than on
     * the entire query string ahead of time, to properly deal with the case
     * where the name or value includes an encoded "=" or "&" character
     * that would otherwise be interpreted as a delimiter.
     *
     * NOTE: byte array data is modified by this method.  Caller beware.
     *
     * @param map Map that accumulates the resulting parameters
     * @param data Input string containing request parameters
     * @param encoding Encoding to use for converting hex
     *
     * @exception UnsupportedEncodingException if the data is malformed
     */
    public static void parseParameters(Map map, byte[] data, String encoding)
                                throws UnsupportedEncodingException {
        if ((data != null) && (data.length > 0)) {
            int ix = 0;
            int ox = 0;
            String key = null;
            String value = null;

            while (ix < data.length) {
                byte c = data[ix++];

                switch ((char) c) {
                    case '&':
                        value = new String(data, 0, ox, encoding);

                        if (key != null) {
                            putMapEntry(map, key, value);
                            key = null;
                        }

                        ox = 0;

                        break;

                    case '=':
                        key = new String(data, 0, ox, encoding);
                        ox = 0;

                        break;

                    case '+':
                        data[ox++] = (byte) ' ';

                        break;

                    case '%':
                        data[ox++] = (byte) ((convertHexDigit(data[ix++]) << 4) +
                                     convertHexDigit(data[ix++]));

                        break;

                    default:
                        data[ox++] = c;
                }
            }

            //The last value does not end in '&'.  So save it now.
            if (key != null) {
                value = new String(data, 0, ox, encoding);
                putMapEntry(map, key, value);
            }
        }
    }

    /**
     * Convert a byte character value to hexidecimal digit value.
     *
     * @param b the character value byte
     */
    private static byte convertHexDigit(byte b) {
        if ((b >= '0') && (b <= '9')) {
            return (byte) (b - '0');
        }

        if ((b >= 'a') && (b <= 'f')) {
            return (byte) (b - 'a' + 10);
        }

        if ((b >= 'A') && (b <= 'F')) {
            return (byte) (b - 'A' + 10);
        }

        return 0;
    }

    boolean isOneDotOne(String protocol) {
        if (protocol == null) {
            return false;
        }

        if (protocol.endsWith("1.1")) {
            return true;
        }

        return false;
    }

    String getPathInfo(HttpServletRequest req) {
        StringTokenizer t = new StringTokenizer(req.getContextPath(), "/");
        int prefixTokens = t.countTokens();

        t = new StringTokenizer(req.getServletPath(), "/");
        prefixTokens += t.countTokens();

        t = new StringTokenizer(req.getRequestURI(), "/");

        int uriTokens = t.countTokens();
        StringBuffer pathbuffer = new StringBuffer();

        for (int i = 0; i < uriTokens; i++) {
            String token = t.nextToken();

            if (i < prefixTokens) {
                continue;
            }

            if (i > prefixTokens) {
                pathbuffer.append("/");
            }

            if ((token.indexOf("+") == -1) && (token.indexOf("%") == -1)) {
                pathbuffer.append(token);
            } else {
                pathbuffer.append(URLDecoder.decode(token));
            }
        }

        return pathbuffer.toString();
    }

    /**
     *
     *
     * @return ...
     */
    public String getServletInfo() {
        return new String("Helma Servlet Client");
    }
}
