// Property.java
// Copyright (c) Hannes Walln�fer 1997-2000

package helma.objectmodel;

import helma.util.*;
import java.util.*;
import java.io.*;
import java.text.*;

/**
 * A property implementation for Nodes stored inside a database. 
 */
 
public final class Property implements IProperty, Serializable, Cloneable {

 
    protected String propname;
    protected Node node;

    public String svalue;
    public boolean bvalue;
    public long lvalue;
    public double dvalue;
    public INode nvalue;
    public Object jvalue;

    public int type;


    public Property (Node node) {
	this.node = node;
    }

    public Property (String propname, Node node) {
	this.propname = propname;
	this.node = node;
    }

    public String getName () {
	return propname;
    }

    public Object getValue () {
	switch (type) {
	case STRING:
	    return svalue;
	case BOOLEAN:
	    return new Boolean (bvalue);
	case INTEGER:
	    return new Long (lvalue);
	case FLOAT:
	    return new Double (dvalue);
	case DATE:
	    return new Date (lvalue);
	case NODE:
	    return nvalue;
	case JAVAOBJECT:
	    return jvalue;
	}
	return null;
    }

    public void setStringValue (String value) throws ParseException {
	if (type == NODE)
	    unregisterNode ();
	// IServer.getLogger().log ("setting string value of property "+propname + " to "+value);
	if (type == DATE) {
	    SimpleDateFormat dateformat = new SimpleDateFormat ();
	    dateformat.setLenient (true);
	    Date date = dateformat.parse (value);
	    this.lvalue =  date.getTime ();
	    return;
	}
	if (type == BOOLEAN) {
	    if ("true".equalsIgnoreCase (value))
	        this.bvalue = true;
	    else if ("false".equalsIgnoreCase (value))
	        this.bvalue = false;
	    return;
	}
	if (type == INTEGER) {
	    this.lvalue = Long.parseLong (value);
	    return;
	}
	if (type == FLOAT) {
	    this.dvalue = new Double (value).doubleValue ();
	    return;
	}
	if (type == JAVAOBJECT)
	    this.jvalue = null;
	this.svalue = value;
	type = STRING;
    }

    public void setIntegerValue (long value) {
	if (type == NODE)
	    unregisterNode ();
	if (type == JAVAOBJECT)
	    this.jvalue = null;
	type = INTEGER;
	this.lvalue = value;
    }

    public void setFloatValue (double value) {
	if (type == NODE)
	    unregisterNode ();
	if (type == JAVAOBJECT)
	    this.jvalue = null;
	type = FLOAT;
	this.dvalue = value;
    }

    public void setDateValue (Date value) {
	if (type == NODE)
	    unregisterNode ();
	if (type == JAVAOBJECT)
	    this.jvalue = null;
	type = DATE;
	this.lvalue = value.getTime();
    }

    public void setBooleanValue (boolean value) {
	if (type == NODE)
	    unregisterNode ();
	if (type == JAVAOBJECT)
	    this.jvalue = null;
	type = BOOLEAN;
	this.bvalue = value;
    }

    public void setNodeValue (INode value) {
	if (type == JAVAOBJECT)
	    this.jvalue = null;
	if (type == NODE && nvalue != value) {
	    unregisterNode ();
	    registerNode (value);
	} else if (type != NODE) 
	    registerNode (value);	
	type = NODE;
	this.nvalue = value;
    }

    public void setJavaObjectValue (Object value) {
	if (type == NODE)
	    unregisterNode ();
	type = JAVAOBJECT;
	this.jvalue = value;
    }


    /**
     * tell a the value node that it is no longer used as a property.
     */
    protected void unregisterNode () {
	if (nvalue != null && nvalue instanceof Node) {
	    Node n = (Node) nvalue;
	    if (!n.anonymous && propname.equals (n.getName()) && this.node == n.getParent()) {
	        // this is the "main" property of a named node, so handle this as a total delete.
	        IServer.getLogger().log ("deleting named property");
	        if (n.proplinks != null) {
	            for (Enumeration e = n.proplinks.elements (); e.hasMoreElements (); ) {
	            	   Property p = (Property) e.nextElement ();
	            	   p.node.propMap.remove (p.propname.toLowerCase ());
	            }
	        }
	        if (n.links != null) {
	            for (Enumeration e = n.links.elements (); e.hasMoreElements (); ) {
	            	   Node n2 = (Node) e.nextElement ();
	            	   n2.releaseNode (n);
	            }
	        }

	    } else {
	        n.unregisterPropLink (this);
	    }
	}
    }

    /**
     * tell the value node that it is being used as a property value.
     */
    protected void registerNode (INode n) {
	if (n != null && n instanceof Node) 
	    ((Node) n).registerPropLink (this);
    }

    public String getStringValue () {
	switch (type) {
	case STRING:
	    return svalue;
	case BOOLEAN:
	    return "" + bvalue;
	case DATE:
	    SimpleDateFormat format = new SimpleDateFormat ("dd.MM.yy hh:mm");
	    return format.format (new Date (lvalue));
	case INTEGER:
	    return Long.toString (lvalue);
	case FLOAT:
	    return Double.toString (dvalue);
	case NODE:
	    return nvalue.getName ();
	case JAVAOBJECT:
	    return jvalue.toString ();
	}
	return "";
    }

    public String toString () {
	return getStringValue ();
    }

    public long getIntegerValue () {
	if (type == INTEGER) 	
	    return lvalue;
	return 0;
    }

    public double getFloatValue () {
	if (type == FLOAT) 	
	    return dvalue;
	return 0.0;
    }


    public Date getDateValue () {
	if (type == DATE) 	
	    return new Date (lvalue);
	return null;
    }

    public boolean getBooleanValue () {
	if (type == BOOLEAN) 
	    return bvalue;
	return false;
    }

    public INode getNodeValue () {
	if (type == NODE) 
	    return nvalue;
	return null;
    }

    public Object getJavaObjectValue () {
	if (type == JAVAOBJECT)
	    return jvalue;
	return null;
    }

    public String getEditor () {
	switch (type) {
	case STRING:
	    return "password".equalsIgnoreCase (propname) ? 
	        "<input type=password name=\""+propname+"\" value='"+ svalue.replace ('\'', '"') +"'>" : 
	        "<input type=text name=\""+propname+"\" value='"+ svalue.replace ('\'', '"') +"'>" ;
	case BOOLEAN:
	    return "<select name=\""+propname+"\"><option selected value="+bvalue+">"+bvalue+"</option><option value="+!bvalue+">"+!bvalue+"</option></select>";
	case INTEGER:
	    return "<input type=text name=\""+propname+"\" value=\""+lvalue+"\">" ;
	case FLOAT:
	    return "<input type=text name=\""+propname+"\" value=\""+dvalue+"\">" ;
	case DATE:
	    SimpleDateFormat format = new SimpleDateFormat ("dd.MM.yy hh:mm");
	    String date =  format.format (new Date (lvalue));
	    return "<input type=text name=\""+propname+"\" value=\""+date+"\">";
	case NODE:
	    return "<input type=text size=25 name="+propname+" value='"+ nvalue.getFullName () +"'>";
	}
	return "";
    }

    private String escape (String s) {
	char c[] = new char[s.length()];
	s.getChars (0, c.length, c, 0);
	StringBuffer b = new StringBuffer ();
	int copyfrom = 0;
	for (int i = 0; i < c.length; i++) {
	    switch (c[i]) {
	        case '\\': 
	        case '"':
	            if (i-copyfrom > 0)
	                b.append (c, copyfrom, i-copyfrom);
	            b.append ('\\');
	            b.append (c[i]);
	            copyfrom = i+1;
	    }   
	}
	if (c.length-copyfrom > 0)
	    b.append (c, copyfrom, c.length-copyfrom);
	return b.toString ();
    }

    public int getType () {
	return type;
    }

    public String getTypeString () {
	switch (type) {
	case STRING:
	    return "string";
	case BOOLEAN:
	    return "boolean";
	case DATE:
	    return "date";
	case INTEGER:
	    return "integer";
	case FLOAT:
	    return "float";
	case NODE:
	    return "node";
	}
	return "";
    }


    public Object clone () {
	try {
	    Property c = (Property) super.clone();
	    c.propname = this.propname;
	    c.svalue = this.svalue;
	    c.bvalue = this.bvalue;
	    c.lvalue = this.lvalue;
	    c.dvalue = this.dvalue;
	    c.type = this.type;
	    return c;
	} catch (CloneNotSupportedException e) { 
	    // this shouldn't happen, since we are Cloneable
	    throw new InternalError ();
	}
    }

}
