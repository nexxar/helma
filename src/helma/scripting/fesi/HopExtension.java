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

package helma.scripting.fesi;

import FESI.Data.*;
import FESI.Exceptions.*;
import FESI.Extensions.*;
import FESI.Interpreter.*;
import helma.framework.*;
import helma.framework.IPathElement;
import helma.framework.core.*;
import helma.objectmodel.*;
import helma.scripting.ScriptingException;
import helma.util.*;
import org.xml.sax.InputSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * This is the basic Extension for FESI interpreters used in Helma. It sets up
 * varios constructors, global functions and properties on the HopObject prototype
 * (Node objects), the global prototype, the session object etc.
 */
public final class HopExtension {
    protected Application app;
    protected FesiEngine engine;

    // previously in FESI.Data.NumberObject
    private Hashtable formatTable = new Hashtable();

    /**
     * Creates a new HopExtension object.
     *
     * @param app ...
     */
    public HopExtension(Application app) {
        this.app = app;
    }

    /**
     * Called by the evaluator after the extension is loaded.
     */
    public void initializeExtension(FesiEngine engine)
                             throws EcmaScriptException {
        this.engine = engine;

        Evaluator evaluator = engine.getEvaluator();
        GlobalObject go = evaluator.getGlobalObject();
        FunctionPrototype fp = (FunctionPrototype) evaluator.getFunctionPrototype();

        ESObject op = evaluator.getObjectPrototype();

        // The editor functions for String, Boolean and Number are deprecated!
        ESObject sp = evaluator.getStringPrototype();

        sp.putHiddenProperty("editor", new StringEditor("editor", evaluator, fp));

        ESObject np = evaluator.getNumberPrototype();

        np.putHiddenProperty("editor", new NumberEditor("editor", evaluator, fp));
        np.putHiddenProperty("format", new NumberPrototypeFormat("format", evaluator, fp));

        ESObject bp = evaluator.getBooleanPrototype();

        bp.putHiddenProperty("editor", new BooleanEditor("editor", evaluator, fp));

        ESObject dp = evaluator.getDatePrototype();

        dp.putHiddenProperty("format", new DatePrototypeFormat("format", evaluator, fp));

        sp.putHiddenProperty("trim", new StringTrim("trim", evaluator, fp));

        // generic (Java wrapper) object prototype
        ObjectPrototype esObjectPrototype = new ObjectPrototype(op, evaluator);

        // the Node prototype
        ObjectPrototype esNodePrototype = new ObjectPrototype(op, evaluator);

        // the Session prototype
        ObjectPrototype esSessionPrototype = new ObjectPrototype(esNodePrototype,
                                                                 evaluator);

        // the Node constructor
        ESObject node = new NodeConstructor("Node", fp, engine);

        // register the default methods of Node objects in the Node prototype
        esNodePrototype.putHiddenProperty("add", new NodeAdd("add", evaluator, fp));
        esNodePrototype.putHiddenProperty("addAt", new NodeAddAt("addAt", evaluator, fp));
        esNodePrototype.putHiddenProperty("remove",
                                          new NodeRemove("remove", evaluator, fp));
        esNodePrototype.putHiddenProperty("list", new NodeList("list", evaluator, fp));
        esNodePrototype.putHiddenProperty("set", new NodeSet("set", evaluator, fp));
        esNodePrototype.putHiddenProperty("get", new NodeGet("get", evaluator, fp));
        esNodePrototype.putHiddenProperty("count", new NodeCount("count", evaluator, fp));
        esNodePrototype.putHiddenProperty("contains",
                                          new NodeContains("contains", evaluator, fp));
        esNodePrototype.putHiddenProperty("size", new NodeCount("size", evaluator, fp));
        esNodePrototype.putHiddenProperty("editor",
                                          new NodeEditor("editor", evaluator, fp));
        esNodePrototype.putHiddenProperty("path", new NodeHref("path", evaluator, fp));
        esNodePrototype.putHiddenProperty("href", new NodeHref("href", evaluator, fp));
        esNodePrototype.putHiddenProperty("setParent",
                                          new NodeSetParent("setParent", evaluator, fp));
        esNodePrototype.putHiddenProperty("invalidate",
                                          new NodeInvalidate("invalidate", evaluator, fp));
        esNodePrototype.putHiddenProperty("prefetchChildren",
                                          new NodePrefetch("prefetchChildren", evaluator,
                                                           fp));
        esNodePrototype.putHiddenProperty("renderSkin",
                                          new RenderSkin("renderSkin", evaluator, fp,
                                                         false, false));
        esNodePrototype.putHiddenProperty("renderSkinAsString",
                                          new RenderSkin("renderSkinAsString", evaluator,
                                                         fp, false, true));
        esNodePrototype.putHiddenProperty("clearCache",
                                          new NodeClearCache("clearCache", evaluator, fp));

        // default methods for generic Java wrapper object prototype.
        // This is a small subset of the methods in esNodePrototype.
        esObjectPrototype.putHiddenProperty("href", new NodeHref("href", evaluator, fp));
        esObjectPrototype.putHiddenProperty("renderSkin",
                                            new RenderSkin("renderSkin", evaluator, fp,
                                                           false, false));
        esObjectPrototype.putHiddenProperty("renderSkinAsString",
                                            new RenderSkin("renderSkinAsString",
                                                           evaluator, fp, false, true));

        // methods that give access to properties and global user lists
        go.putHiddenProperty("Node", node); // register the constructor for a plain Node object.
        go.putHiddenProperty("HopObject", node); // HopObject is the new name for node.
        go.putHiddenProperty("getProperty",
                             new GlobalGetProperty("getProperty", evaluator, fp));
        go.putHiddenProperty("token", new GlobalGetProperty("token", evaluator, fp));
        go.putHiddenProperty("getAge", new GlobalGetAge("getAge", evaluator, fp));
        go.putHiddenProperty("getURL", new GlobalGetURL("getURL", evaluator, fp));
        go.putHiddenProperty("encode", new GlobalEncode("encode", evaluator, fp));
        go.putHiddenProperty("encodeXml", new GlobalEncodeXml("encodeXml", evaluator, fp));
        go.putHiddenProperty("encodeForm",
                             new GlobalEncodeForm("encodeForm", evaluator, fp));
        go.putHiddenProperty("format", new GlobalFormat("format", evaluator, fp));
        go.putHiddenProperty("stripTags", new GlobalStripTags("stripTags", evaluator, fp));
        go.putHiddenProperty("getXmlDocument",
                             new GlobalGetXmlDocument("getXmlDocument", evaluator, fp));
        go.putHiddenProperty("getHtmlDocument",
                             new GlobalGetHtmlDocument("getHtmlDocument", evaluator, fp));
        go.putHiddenProperty("jdomize", new GlobalJDOM("jdomize", evaluator, fp));
        go.putHiddenProperty("getSkin", new GlobalCreateSkin("getSkin", evaluator, fp));
        go.putHiddenProperty("createSkin",
                             new GlobalCreateSkin("createSkin", evaluator, fp));
        go.putHiddenProperty("renderSkin",
                             new RenderSkin("renderSkin", evaluator, fp, true, false));
        go.putHiddenProperty("renderSkinAsString",
                             new RenderSkin("renderSkinAsString", evaluator, fp, true,
                                            true));
        go.putHiddenProperty("authenticate",
                             new GlobalAuthenticate("authenticate", evaluator, fp));
        go.deleteProperty("exit", "exit".hashCode());

        // register object prototypes with FesiEngine
        engine.putPrototype("global", go);
        engine.putPrototype("hopobject", esNodePrototype);
        engine.putPrototype("__javaobject__", esObjectPrototype);

        //        engine.putPrototype ("session", esSessionPrototype);
    }

    private String getNumberChoice(String name, double from, double to, double step,
                                   double value) {
        double l = 0.000001;
        StringBuffer b = new StringBuffer("<select name=\"");

        // DecimalFormat fmt = new DecimalFormat ();
        b.append(name);
        b.append("\">");

        // check step for finity
        if (step == 0.0) {
            step = 1.0;
        }

        if ((step * (to - from)) < 0) {
            step *= -1;
        }

        double d = (from <= to) ? 1.0 : (-1.0);

        for (double i = from; (i * d) <= (to * d); i += step) {
            if (Math.abs(i % 1) < l) {
                int j = (int) i;

                b.append("<option value=\"" + j);

                if (i == value) {
                    b.append("\" selected=\"true");
                }

                b.append("\">" + j);
            } else {
                b.append("<option value=\"" + i);

                if (i == value) {
                    b.append("\" selected=\"true");
                }

                b.append("\">" + i);
            }
        }

        b.append("</select>");

        return b.toString();
    }

    class NodeAdd extends BuiltinFunctionObject {
        NodeAdd(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESNode node = (ESNode) thisObject;

            return ESBoolean.makeBoolean(node.add(arguments));
        }
    }

    class NodeAddAt extends BuiltinFunctionObject {
        NodeAddAt(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESNode node = (ESNode) thisObject;

            return ESBoolean.makeBoolean(node.addAt(arguments));
        }
    }

    class NodeRemove extends BuiltinFunctionObject {
        NodeRemove(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESNode node = (ESNode) thisObject;

            return ESBoolean.makeBoolean(node.remove(arguments));
        }
    }

    class NodeList extends BuiltinFunctionObject {
        NodeList(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 0);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESNode node = (ESNode) thisObject;

            return node.list();
        }
    }

    class NodeGet extends BuiltinFunctionObject {
        NodeGet(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESValue esv = null;

            if (arguments[0].isNumberValue()) {
                int i = arguments[0].toInt32();

                esv = thisObject.getProperty(i);
            } else {
                String name = arguments[0].toString();

                // call esNodeProperty() method special to ESNode because we want to avoid
                // retrieving prototype functions when calling hopobject.get().
                ESNode esn = (ESNode) thisObject;

                esv = esn.getNodeProperty(name);
            }

            return (esv);
        }
    }

    class NodeSet extends BuiltinFunctionObject {
        NodeSet(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESNode esn = (ESNode) thisObject;

            if (arguments[0].isNumberValue()) {
                return ESBoolean.makeBoolean(esn.addAt(arguments));
            } else {
                String propname = arguments[0].toString();

                esn.putProperty(propname, arguments[1], propname.hashCode());
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class NodeCount extends BuiltinFunctionObject {
        NodeCount(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            INode node = ((ESNode) thisObject).getNode();

            return new ESNumber((double) node.numberOfNodes());
        }
    }

    class NodeContains extends BuiltinFunctionObject {
        NodeContains(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESNode node = (ESNode) thisObject;

            return new ESNumber(node.contains(arguments));
        }
    }

    class NodeInvalidate extends BuiltinFunctionObject {
        NodeInvalidate(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 0);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESNode esn = (ESNode) thisObject;

            return ESBoolean.makeBoolean(esn.invalidate(arguments));
        }
    }

    class NodePrefetch extends BuiltinFunctionObject {
        NodePrefetch(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 0);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            try {
                ESNode esn = (ESNode) thisObject;

                esn.prefetchChildren(arguments);
            } catch (IllegalArgumentException illarg) {
                throw illarg;
            } catch (Exception x) {
                // swallow exceptions in prefetchChildren
            }

            return ESNull.theNull;
        }
    }

    class NodeEditor extends BuiltinFunctionObject {
        NodeEditor(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            if (arguments.length < 1) {
                throw new EcmaScriptException("Missing argument for Node.editor(): Name of property to be edited.");
            }

            String propName = arguments[0].toString();
            ESValue propValue = thisObject.getProperty(propName, propName.hashCode());

            if (propValue.isBooleanValue()) {
                return (booleanEditor(thisObject, propName, propValue, arguments));
            }

            if (propValue.isNumberValue()) {
                return (numberEditor(thisObject, propName, propValue, arguments));
            }

            if (propValue instanceof DatePrototype) {
                return (dateEditor(thisObject, propName, propValue, arguments));
            }

            return (stringEditor(thisObject, propName, propValue, arguments));
        }

        public ESValue stringEditor(ESObject thisObject, String propName,
                                    ESValue propValue, ESValue[] arguments)
                             throws EcmaScriptException {
            String value = null;

            if ((propValue == null) || (ESNull.theNull == propValue) ||
                    (ESUndefined.theUndefined == propValue)) {
                value = "";
            } else {
                value = HtmlEncoder.encodeFormValue(propValue.toString());
            }

            if ((arguments.length == 2) && arguments[1].isNumberValue()) {
                return new ESString("<input type=\"text\" name=\"" + propName +
                                    "\" size=\"" + arguments[1].toInt32() +
                                    "\" value=\"" + value + "\">");
            } else if ((arguments.length == 3) && arguments[1].isNumberValue() &&
                           arguments[2].isNumberValue()) {
                return new ESString("<textarea name=\"" + propName + "\" cols=\"" +
                                    arguments[1].toInt32() + "\" rows=\"" +
                                    arguments[2].toInt32() + "\" wrap=\"virtual\">" +
                                    value + "</textarea>");
            }

            return new ESString("<input type=\"text\" name=\"" + propName +
                                "\" value=\"" + value + "\">");
        }

        public ESValue dateEditor(ESObject thisObject, String propName,
                                  ESValue propValue, ESValue[] arguments)
                           throws EcmaScriptException {
            Date date = (Date) propValue.toJavaObject();
            DateFormat fmt = (arguments.length > 1)
                             ? new SimpleDateFormat(arguments[1].toString())
                             : new SimpleDateFormat();

            return new ESString("<input type=\"text\" name=\"" + propName +
                                "\" value=\"" + fmt.format(date) + "\">");
        }

        public ESValue numberEditor(ESObject thisObject, String propName,
                                    ESValue propValue, ESValue[] arguments)
                             throws EcmaScriptException {
            ESNumber esn = (ESNumber) propValue.toESNumber();
            double value = esn.doubleValue();

            if ((arguments.length == 3) && arguments[1].isNumberValue() &&
                    arguments[2].isNumberValue()) {
                return new ESString(getNumberChoice(propName, arguments[1].toInt32(),
                                                    arguments[2].toInt32(), 1, value));
            } else if ((arguments.length == 4) && arguments[1].isNumberValue() &&
                           arguments[2].isNumberValue() && arguments[3].isNumberValue()) {
                return new ESString(getNumberChoice(propName, arguments[1].doubleValue(),
                                                    arguments[2].doubleValue(),
                                                    arguments[3].doubleValue(), value));
            }

            DecimalFormat fmt = new DecimalFormat();

            if ((arguments.length == 2) && arguments[1].isNumberValue()) {
                return new ESString("<input type=\"text\" name=\"" + propName +
                                    "\" size=\"" + arguments[1].toInt32() +
                                    "\" value=\"" + fmt.format(value) + "\">");
            } else {
                return new ESString("<input type=\"text\" name=\"" + propName +
                                    "\" size=\"8\" value=\"" + fmt.format(value) + "\">");
            }
        }

        public ESValue booleanEditor(ESObject thisObject, String propName,
                                     ESValue propValue, ESValue[] arguments)
                              throws EcmaScriptException {
            ESBoolean esb = (ESBoolean) propValue.toESBoolean();
            String value = esb.toString();

            if (arguments.length < 2) {
                String retval = "<input type=\"checkbox\" name=\"" + propName +
                                "\" value=\"on\"";

                if (esb.booleanValue()) {
                    retval += " checked";
                }

                retval += ">";

                return new ESString(retval);
            }

            boolean forValue = arguments[1].booleanValue();
            StringBuffer retval = new StringBuffer("<input type=\"radio\" name=\"" +
                                                   propName + "\" value=\"");

            if (forValue) {
                retval.append("on");
            }

            retval.append("\"");

            if (forValue == esb.booleanValue()) {
                retval.append(" checked");
            }

            retval.append(">");

            return new ESString(retval.toString());
        }
    }

    class StringEditor extends BuiltinFunctionObject {
        StringEditor(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            throw (new EcmaScriptException("String.editor() wird nicht mehr unterstuetzt. Statt node.strvar.editor(...) kann node.editor(\"strvar\", ...) verwendet werden."));
        }
    }

    class NumberEditor extends BuiltinFunctionObject {
        NumberEditor(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            throw (new EcmaScriptException("Number.editor() wird nicht mehr unterstuetzt. Statt node.nvar.editor(...) kann node.editor(\"nvar\", ...) verwendet werden."));
        }
    }

    class BooleanEditor extends BuiltinFunctionObject {
        BooleanEditor(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            throw (new EcmaScriptException("Boolean.editor() wird nicht mehr unterstuetzt. Statt node.boolvar.editor(...) kann node.editor(\"boolvar\", ...) verwendet werden."));
        }
    }

    // previously in FESI.Data.DateObject
    class DatePrototypeFormat extends BuiltinFunctionObject {
        DatePrototypeFormat(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 0);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            DatePrototype aDate = (DatePrototype) thisObject;

            DateFormat df = (arguments.length > 0)
                            ? new SimpleDateFormat(arguments[0].toString())
                            : new SimpleDateFormat();

            df.setTimeZone(TimeZone.getDefault());

            return (aDate.toJavaObject() == null) ? new ESString("")
                                                  : new ESString(df.format((Date) aDate.toJavaObject()));
        }
    }

    class NumberPrototypeFormat extends BuiltinFunctionObject {
        NumberPrototypeFormat(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 0);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESObject aNumber = (ESObject) thisObject;
            String fstr = "#,##0.00";

            if (arguments.length >= 1) {
                fstr = arguments[0].toString();
            }

            DecimalFormat df = (DecimalFormat) formatTable.get(fstr);

            if (df == null) {
                df = new DecimalFormat(fstr);
                formatTable.put(fstr, df);
            }

            return new ESString(df.format(aNumber.doubleValue()));
        }
    }

    class StringTrim extends BuiltinFunctionObject {
        StringTrim(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            return new ESString(thisObject.toString().trim());
        }
    }

    class GlobalGetProperty extends BuiltinFunctionObject {
        GlobalGetProperty(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            if (arguments.length == 0) {
                return new ESString("");
            }

            String defval = (arguments.length > 1) ? arguments[1].toString() : "";

            return new ESString(app.getProperty(arguments[0].toString(), defval));
        }
    }

    class GlobalAuthenticate extends BuiltinFunctionObject {
        GlobalAuthenticate(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            if (arguments.length != 2) {
                return ESBoolean.makeBoolean(false);
            }

            return ESBoolean.makeBoolean(app.authenticate(arguments[0].toString(),
                                                          arguments[1].toString()));
        }
    }

    /**
     * Get a parsed Skin from an app-managed skin text
     */
    class GlobalCreateSkin extends BuiltinFunctionObject {
        GlobalCreateSkin(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            if ((arguments.length != 1) || ESNull.theNull.equals(arguments[0])) {
                throw new EcmaScriptException("createSkin must be called with one String argument");
            }

            String str = arguments[0].toString();
            Skin skin = new Skin(str, app);

            return new ESWrapper(skin, evaluator);
        }
    }

    /**
     * Render a skin
     */
    class RenderSkin extends BuiltinFunctionObject {
        boolean global;
        boolean asString;

        RenderSkin(String name, Evaluator evaluator, FunctionPrototype fp,
                   boolean global, boolean asString) {
            super(fp, evaluator, name, 1);
            this.global = global;
            this.asString = asString;
        }

        public ESValue callFunction(ESObject thisObj, ESValue[] arguments)
                             throws EcmaScriptException {
            if ((arguments.length < 1) || (arguments.length > 2) ||
                    (arguments[0] == null) || (arguments[0] == ESNull.theNull)) {
                throw new EcmaScriptException("renderSkin requires one argument containing the skin name and an optional parameter object argument");
            }

            try {
                Skin skin = null;
                ESObject thisObject = global ? null : thisObj;
                HashMap params = null;

                if ((arguments.length > 1) && arguments[1] instanceof ESObject) {
                    // create an parameter object to pass to the skin
                    ESObject paramObject = (ESObject) arguments[1];

                    params = new HashMap();

                    for (Enumeration en = paramObject.getProperties();
                             en.hasMoreElements();) {
                        String propname = (String) en.nextElement();

                        params.put(propname,
                                   paramObject.getProperty(propname, propname.hashCode())
                                              .toJavaObject());
                    }
                }

                // first, see if the first argument already is a skin object. If not, it's the name of the skin to be called
                if (arguments[0] instanceof ESWrapper) {
                    Object obj = ((ESWrapper) arguments[0]).toJavaObject();

                    if (obj instanceof Skin) {
                        skin = (Skin) obj;
                    }
                }

                // retrieve res.skinpath, an array of objects that tell us where to look for skins
                // (strings for directory names and INodes for internal, db-stored skinsets)
                ResponseTrans res = engine.getResponse();
                Object[] skinpath = res.getSkinpath();

                // ready... retrieve the skin and render it.
                Object javaObject = (thisObject == null) ? null : thisObject.toJavaObject();

                if (skin == null) {
                    String skinid = app.getPrototypeName(javaObject) + "/" +
                                    arguments[0].toString();

                    skin = res.getCachedSkin(skinid);

                    if (skin == null) {
                        skin = app.getSkin(javaObject, arguments[0].toString(), skinpath);
                        res.cacheSkin(skinid, skin);
                    }
                }

                if (asString) {
                    res.pushStringBuffer();
                }

                if (skin != null) {
                    skin.render(engine.getRequestEvaluator(), javaObject, params);
                } else {
                    res.write("[Skin not found: " + arguments[0] + "]");
                }

                if (asString) {
                    return new ESString(res.popStringBuffer());
                }
            } catch (RedirectException redir) {
                // let redirect pass through
                throw redir;
            } catch (Exception x) {
                x.printStackTrace();
                throw new EcmaScriptException("renderSkin: " + x);
            }

            return ESNull.theNull;
        }
    }

    class GlobalGetAge extends BuiltinFunctionObject {
        GlobalGetAge(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            if ((arguments.length != 1) || !(arguments[0] instanceof DatePrototype)) {
                throw new EcmaScriptException("Invalid arguments for function getAge(Date)");
            }

            Date d = (Date) arguments[0].toJavaObject();

            try {
                long then = d.getTime() / 60000L;
                long now = System.currentTimeMillis() / 60000L;
                StringBuffer age = new StringBuffer();
                String divider = "vor ";
                long diff = now - then;
                long days = diff / 1440;

                if (days > 0) {
                    age.append((days > 1) ? (divider + days + " Tagen") : (divider +
                                          "1 Tag"));
                    divider = ", ";
                }

                long hours = (diff % 1440) / 60;

                if (hours > 0) {
                    age.append((hours > 1) ? (divider + hours + " Stunden")
                                           : (divider + "1 Stunde"));
                    divider = ", ";
                }

                long minutes = diff % 60;

                if (minutes > 0) {
                    age.append((minutes > 1) ? (divider + minutes + " Minuten")
                                             : (divider + "1 Minute"));
                }

                return new ESString(age.toString());
            } catch (Exception e) {
                app.logEvent("Error formatting date: " + e);
                e.printStackTrace();

                return new ESString("");
            }
        }
    }

    class GlobalGetURL extends BuiltinFunctionObject {
        GlobalGetURL(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            if (arguments.length < 1) {
                return ESNull.theNull;
            }

            InputStream inStream = null;
            try {
                URL url = new URL(arguments[0].toString());
                URLConnection con = url.openConnection();

                // do we have if-modified-since or etag headers to set?
                if (arguments.length > 1) {
                    if (arguments[1] instanceof DatePrototype) {
                        Date date = (Date) arguments[1].toJavaObject();

                        con.setIfModifiedSince(date.getTime());

                        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",
                                                                       Locale.UK);

                        format.setTimeZone(TimeZone.getTimeZone("GMT"));
                        con.setRequestProperty("If-Modified-Since", format.format(date));
                    } else if (arguments[1] != null) {
                        con.setRequestProperty("If-None-Match", arguments[1].toString());
                    }
                }

                String httpUserAgent = app.getProperty("httpUserAgent");

                if (httpUserAgent != null) {
                    con.setRequestProperty("User-Agent", httpUserAgent);
                }

                con.setAllowUserInteraction(false);

                String filename = url.getFile();
                String contentType = con.getContentType();
                long lastmod = con.getLastModified();
                String etag = con.getHeaderField("ETag");
                int length = con.getContentLength();
                int resCode = 0;

                if (con instanceof HttpURLConnection) {
                    resCode = ((HttpURLConnection) con).getResponseCode();
                }

                ByteArrayOutputStream body = new ByteArrayOutputStream();

                if ((length != 0) && (resCode != 304)) {
                    InputStream in = new BufferedInputStream(con.getInputStream());
                    inStream = in;
                    byte[] b = new byte[1024];
                    int read;

                    while ((read = in.read(b)) > -1)
                        body.write(b, 0, read);

                    in.close();
                }

                MimePart mime = new MimePart(filename, body.toByteArray(), contentType);

                if (lastmod > 0) {
                    mime.lastModified = new Date(lastmod);
                }

                mime.eTag = etag;

                return ESLoader.normalizeObject(mime, evaluator);
            } catch (Exception ignore) {
                if (inStream != null) {
                    try {
                        inStream.close();
                    } catch(Exception ex) {}
                }
            }

            return ESNull.theNull;
        }
    }

    class GlobalEncode extends BuiltinFunctionObject {
        GlobalEncode(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            if (arguments.length < 1) {
                return ESNull.theNull;
            }

            return new ESString(HtmlEncoder.encodeAll(arguments[0].toString()));
        }
    }

    class GlobalEncodeXml extends BuiltinFunctionObject {
        GlobalEncodeXml(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            if (arguments.length < 1) {
                return ESNull.theNull;
            }

            return new ESString(HtmlEncoder.encodeXml(arguments[0].toString()));
        }
    }

    class GlobalEncodeForm extends BuiltinFunctionObject {
        GlobalEncodeForm(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            if (arguments.length < 1) {
                return ESNull.theNull;
            }

            return new ESString(HtmlEncoder.encodeFormValue(arguments[0].toString()));
        }
    }

    // strip tags from XML/HTML text
    class GlobalStripTags extends BuiltinFunctionObject {
        GlobalStripTags(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            if (arguments.length < 1) {
                return ESNull.theNull;
            }

            StringBuffer b = new StringBuffer();
            char[] c = arguments[0].toString().toCharArray();
            boolean inTag = false;

            for (int i = 0; i < c.length; i++) {
                if (c[i] == '<') {
                    inTag = true;
                }

                if (!inTag) {
                    b.append(c[i]);
                }

                if (c[i] == '>') {
                    inTag = false;
                }
            }

            return new ESString(b.toString());
        }
    }

    class GlobalFormat extends BuiltinFunctionObject {
        GlobalFormat(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            if (arguments.length < 1) {
                return ESNull.theNull;
            }

            return new ESString(HtmlEncoder.encode(arguments[0].toString()));
        }
    }

    class GlobalGetXmlDocument extends BuiltinFunctionObject {
        GlobalGetXmlDocument(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            try {
                Object p = arguments[0].toJavaObject();
                Object doc = helma.util.XmlUtils.parseXml(p);

                return ESLoader.normalizeObject(doc, evaluator);
            } catch (Exception noluck) {
                app.logEvent("Error creating XML document: " + noluck);
            }

            return ESNull.theNull;
        }
    }

    class GlobalGetHtmlDocument extends BuiltinFunctionObject {
        GlobalGetHtmlDocument(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            try {
                Object p = arguments[0].toJavaObject();
                Object doc = helma.util.XmlUtils.parseHtml(p);

                return ESLoader.normalizeObject(doc, evaluator);
            } catch (Exception noluck) {
                app.logEvent("Error creating HTML document: " + noluck);
            }

            return ESNull.theNull;
        }
    }

    class GlobalJDOM extends BuiltinFunctionObject {
        GlobalJDOM(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            try {
                Class.forName("org.w3c.dom.Document");

                org.w3c.dom.Document doc = (org.w3c.dom.Document) arguments[0].toJavaObject();

                Class.forName("org.jdom.input.DOMBuilder");

                org.jdom.input.DOMBuilder builder = new org.jdom.input.DOMBuilder();

                return ESLoader.normalizeObject(builder.build(doc), evaluator);
            } catch (Exception noluck) {
                app.logEvent("Error building JDOM document: " + noluck);
            }

            return ESNull.theNull;
        }
    }

    class NodeSetParent extends BuiltinFunctionObject {
        NodeSetParent(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 2);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESNode node = (ESNode) thisObject;

            return ESBoolean.makeBoolean(node.setParent(arguments));
        }
    }

    class NodeClearCache extends BuiltinFunctionObject {
        NodeClearCache(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 2);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESNode node = (ESNode) thisObject;

            return ESBoolean.makeBoolean(node.clearCache());
        }
    }

    class NodeHref extends BuiltinFunctionObject {
        NodeHref(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            Object elem = thisObject.toJavaObject();
            String tmpname = (arguments.length == 0) ? "" : arguments[0].toString();
            String basicHref = app.getNodeHref(elem, tmpname);

            // check if the app.properties specify a href-function to post-process the
            // basic href.
            String hrefFunction = app.getProperty("hrefFunction", null);

            if (hrefFunction != null) {
                Object funcElem = elem;

                while (funcElem != null) {
                    if (engine.hasFunction(funcElem, hrefFunction)) {
                        Object obj;

                        try {
                            obj = engine.invoke(funcElem, hrefFunction,
                                                new Object[] { basicHref }, false);
                        } catch (ScriptingException x) {
                            throw new RuntimeException("Error in hrefFunction: " + x);
                        }

                        if (obj == null) {
                            throw new RuntimeException("hrefFunction " + hrefFunction +
                                                       " returned null");
                        }

                        basicHref = obj.toString();

                        break;
                    }

                    funcElem = app.getParentElement(funcElem);
                }
            }

            // check if the app.properties specify a href-skin to post-process the
            // basic href.
            String hrefSkin = app.getProperty("hrefSkin", null);

            if (hrefSkin != null) {
                // we need to post-process the href with a skin for this application
                // first, look in the object href was called on.
                Object skinElem = elem;
                Skin skin = null;

                // ResponseTrans res = engine.getResponse();
                // Object[] skinpath = res.getSkinpath ();
                while ((skin == null) && (skinElem != null)) {
                    Prototype proto = app.getPrototype(skinElem);

                    if (proto != null) {
                        skin = proto.getSkin(hrefSkin);
                    }

                    if (skin == null) {
                        skinElem = app.getParentElement(skinElem);
                    }
                }

                if (skin != null) {
                    basicHref = renderSkin(skin, basicHref, skinElem);
                }
            }

            return new ESString(basicHref);
        }

        private String renderSkin(Skin skin, String path, Object skinElem)
                           throws EcmaScriptException {
            engine.getResponse().pushStringBuffer();

            HashMap param = new HashMap();

            param.put("path", path);
            skin.render(engine.getRequestEvaluator(), skinElem, param);

            return engine.getResponse().popStringBuffer().trim();
        }
    }
}
