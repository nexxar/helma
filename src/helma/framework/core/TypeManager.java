// TypeManager.java
// Copyright (c) Hannes Walln�fer 1998-2000
 
package helma.framework.core;

import helma.objectmodel.*;
import helma.util.*;
import FESI.Parser.*;
import FESI.AST.ASTFormalParameterList;
import FESI.AST.ASTStatementList;
import FESI.AST.EcmaScriptTreeConstants;
import FESI.Interpreter.*;
import FESI.Exceptions.*;
import FESI.Extensions.*;
import FESI.Data.*;
import java.util.*;
import java.io.*;


/**
 * The type manager periodically checks the prototype definitions for its 
 * applications and updates the evaluators if anything has changed.
 */

public class TypeManager implements Runnable, EcmaScriptTreeConstants {

    Application app;
    File appDir;
    Hashtable prototypes;
    Prototype nodeProto;
    long idleSeconds = 120; // if idle for longer than 5 minutes, slow down
    boolean rewire;

    Thread typechecker;

   // The http broadcaster for pushing out parser output
   // static WebBroadcaster broadcaster;
   // static {
   //   try {
   //      broadcaster = new WebBroadcaster (9999);
   //   } catch (IOException ignore) {}
   // }


    public TypeManager (Application app) {
	this.app = app;
	appDir = app.appDir;
	File f = new File (appDir, "user");
	if (!f.exists())	
	    f.mkdir ();
	f = new File (appDir, "root");
	if (!f.exists())	
	    f.mkdir ();
	f = new File (appDir, "global");
	if (!f.exists())	
	    f.mkdir ();
	prototypes = new Hashtable ();
	nodeProto = null;
    }


    public void check () {
	// long now = System.currentTimeMillis ();
	// System.out.print ("checking "+Thread.currentThread ());
	try {
	    String[] list = appDir.list ();
	    for (int i=0; i<list.length; i++) {
	        File protoDir = new File (appDir, list[i]);
	        // cut out ".." and other directories that contain "."
	        if (isValidTypeName (list[i]) && protoDir.isDirectory ()) {
	            Prototype proto = getPrototype (list[i]);
	            if (proto != null) {
	                // check if existing prototype needs update
	                // IServer.getLogger().log (protoDir.lastModified ());
	                updatePrototype (list[i], protoDir, proto);
	            } else {
	                // create new prototype
	                proto = new Prototype (protoDir, app);
	                registerPrototype (list[i], protoDir, proto);
	                prototypes.put (list[i], proto);
	                if ("node".equalsIgnoreCase (list[i]))
	                    nodeProto = proto;
	                // give logger thread a chance to tell what's going on
	                Thread.yield();
	            }
	        }
	    }

	} catch (Exception ignore) {
	    IServer.getLogger().log (this+": "+ignore);
	}

	if (rewire) {
	    // there have been changes @ DbMappings
	    app.rewireDbMappings ();
	    rewire = false;
	}
	// IServer.getLogger().log (" ...done @ "+ (System.currentTimeMillis () - now)+ "--- "+idleSeconds);
    }


    private boolean isValidTypeName (String str) {
    	if (str == null)
    	    return false;
    	int l = str.length ();
    	if (l == 0)
    	    return false;
	for (int i=0; i<l; i++)
	    if (!Character.isJavaIdentifierPart (str.charAt (i)))
	        return false;
	return true;
    }

    public void start () {
    	stop ();
	typechecker = new Thread (this, "Typechecker-"+app.getName());
	typechecker.setPriority (Thread.MIN_PRIORITY);
	typechecker.start ();
    }

    public void stop () {
	if (typechecker != null && typechecker.isAlive ())
	    typechecker.interrupt ();
	typechecker = null;
    }

    public Prototype getPrototype (String typename) {
	return (Prototype) prototypes.get (typename);
    }


    public void run () {

	while (Thread.currentThread () == typechecker) {
	    idleSeconds++;
	    try {
	        // for each idle minute, add 300 ms to sleeptime until 5 secs are reached.
	        // (10 secs are reached after 30 minutes of idle state)
	        // the above is all false.
	        long sleeptime = 1500 + Math.min (idleSeconds*30, 3500);
	        typechecker.sleep (sleeptime);
	    } catch (InterruptedException x) {
	        // IServer.getLogger().log ("Typechecker interrupted");
	        break;
	    }
	    check ();
	}
    }


    public void registerPrototype (String name, File dir, Prototype proto) {
        // IServer.getLogger().log ("registering prototype "+name);

        int size = app.allThreads.size ();
        for (int i=0; i<size; i++) {
            RequestEvaluator reval = (RequestEvaluator) app.allThreads.elementAt (i);
            ObjectPrototype op = null;
            if ("user".equalsIgnoreCase (name))
                op = reval.esUserPrototype;
            else if ("global".equalsIgnoreCase (name))
                op = reval.global;
            else if ("node".equalsIgnoreCase (name))
                op = reval.esNodePrototype;
            else {
                op = new ObjectPrototype (reval.esNodePrototype, reval.evaluator);
                try {
                    op.putProperty ("prototypename", new ESString (name), "prototypename".hashCode ());
                } catch (EcmaScriptException ignore) {}
            }
            reval.putPrototype (name, op);

            // Register a constructor for all types except global.
            // This will first create a node and then call the actual (scripted) constructor on it.
            if (!"global".equalsIgnoreCase (name)) {
                try {
                    FunctionPrototype fp = (FunctionPrototype) reval.evaluator.getFunctionPrototype();
                    reval.global.putHiddenProperty (name, new NodeConstructor (name, fp, reval));
                } catch (EcmaScriptException ignore) {}
            }
        }

        // show the type checker thread that there has been type activity
        idleSeconds = 0;

        String list[] = dir.list();
        Hashtable ntemp = new Hashtable ();
        Hashtable nfunc = new Hashtable ();
        Hashtable nact = new Hashtable ();

        for (int i=0; i<list.length; i++) {
            File tmpfile = new File (dir, list[i]);
            int dot = list[i].indexOf (".");

            if (dot < 0)
                continue;

            String tmpname = list[i].substring(0, dot);

            if (list[i].endsWith (app.templateExtension)) {
                try {
                    Template t = new Template (tmpfile, tmpname, proto);
                    ntemp.put (tmpname, t);
                } catch (Throwable x) {
                    IServer.getLogger().log ("Error creating prototype: "+x);
                    // broadcaster.broadcast ("Error creating prototype "+list[i]+":<br>"+x+"<br><hr>");
                }

            } else if (list[i].endsWith (app.scriptExtension) && tmpfile.length () > 0) {
                try {
                    FunctionFile ff = new FunctionFile (tmpfile, tmpname, proto);
                    nfunc.put (tmpname, ff);
                } catch (Throwable x) {
                    IServer.getLogger().log ("Error creating prototype: "+x);
                    // broadcaster.broadcast ("Error creating prototype "+list[i]+":<br>"+x+"<br><hr>");
                }
            } else if (list[i].endsWith (app.actionExtension) && tmpfile.length () > 0) {
                try {
                    Action af = new Action (tmpfile, tmpname, proto);
                    nact.put (tmpname, af);
                } catch (Throwable x) {
                    IServer.getLogger().log ("Error creating prototype: "+x);
                    // broadcaster.broadcast ("Error creating prototype "+list[i]+":<br>"+x+"<br><hr>");
                }
           }
        }
        proto.templates = ntemp;
        proto.functions = nfunc;
        proto.actions = nact;
    }


    public void updatePrototype (String name, File dir, Prototype proto) {
        // IServer.getLogger().log ("updating prototype "+name);

        String list[] = dir.list();
        Hashtable ntemp = new Hashtable ();
        Hashtable nfunc = new Hashtable ();
        Hashtable nact = new Hashtable ();

        for (int i=0; i<list.length; i++) {
            File tmpfile = new File (dir, list[i]);
            int dot = list[i].indexOf (".");

            if (dot < 0)
                continue;

            String tmpname = list[i].substring(0, dot);
            if (list[i].endsWith (app.templateExtension)) {
                Template t = proto.getTemplate (tmpname);
	   try {
                    if (t == null) {
                        t = new Template (tmpfile, tmpname, proto);
                        idleSeconds = 0;
	       } else if (t.lastmod != tmpfile.lastModified ()) {
                        t.update (tmpfile);
                        idleSeconds = 0;
                    }
                } catch (Throwable x) {
                    IServer.getLogger().log ("Error updating prototype: "+x);
                    // broadcaster.broadcast ("Error updating prototype "+list[i]+":<br>"+x+"<br><hr>");
                }
                ntemp.put (tmpname, t);

            } else if (list[i].endsWith (app.scriptExtension) && tmpfile.length () > 0) {
                FunctionFile ff = proto.getFunctionFile (tmpname);
                try {
                    if (ff == null) {
                        ff = new FunctionFile (tmpfile, tmpname, proto);
	           idleSeconds = 0;
                    } else if (ff.lastmod != tmpfile.lastModified ()) {
                        ff.update (tmpfile);
	           idleSeconds = 0;
                    }
                } catch (Throwable x) {
                    IServer.getLogger().log ("Error updating prototype: "+x);
                    // broadcaster.broadcast ("Error updating prototype "+list[i]+":<br>"+x+"<br><hr>");
                }
                nfunc.put (tmpname, ff);

           }  else if (list[i].endsWith (app.actionExtension) && tmpfile.length () > 0) {
                Action af = proto.getAction (tmpname);
                try {
                    if (af == null) {
                        af = new Action (tmpfile, tmpname, proto);
	           idleSeconds = 0;
                    } else if (af.lastmod != tmpfile.lastModified ()) {
                        af.update (tmpfile);
	           idleSeconds = 0;
                    }
                } catch (Throwable x) {
                    IServer.getLogger().log ("Error updating prototype: "+x);
                    // broadcaster.broadcast ("Error updating prototype "+list[i]+":<br>"+x+"<br><hr>");
                }
                nact.put (tmpname, af);

           } else if ("type.properties".equalsIgnoreCase (list[i])) {
	    try {
	        if (proto.dbmap.read ()) {
	            idleSeconds = 0;
	            rewire = true;
	        }
	    } catch (Exception ignore) {
	        IServer.getLogger().log ("Error updating db mapping for type "+name+": "+ignore);
	    }
	}
        }
        proto.templates = ntemp;
        proto.functions = nfunc;
        proto.actions = nact;
    }

    protected void readFunctionFile (File f, String protoname) {

        EvaluationSource es = new FileEvaluationSource(f.getPath(), null);
        FileReader fr = null;

        int size = app.allThreads.size ();
        for (int i=0; i<size; i++) {
            RequestEvaluator reval = (RequestEvaluator) app.allThreads.elementAt (i);

            try {
            	    fr = new FileReader(f);
                 ObjectPrototype op = reval.getPrototype (protoname);
                 reval.evaluator.evaluate(fr, op, es, false);
            } catch (IOException e) {
                IServer.getLogger().log ("*** Error reading function file "+f+": "+e);
            } catch (EcmaScriptException e) {
                IServer.getLogger().log ("*** Error reading function file "+f+": "+e);
            } finally {
                if (fr!=null) {
                    try {
                        fr.close();
                    } catch (IOException ignore) {}
                }
            }
        }

    }

    protected void readFunction (String funcname, String params, String body, String protoname)
		throws EcmaScriptException {

        // ESObject fp = app.eval.evaluator.getFunctionPrototype();
        ConstructedFunctionObject function = null;
        ASTFormalParameterList fpl = null;
        ASTStatementList sl = null;

        if (body == null || "".equals (body.trim()))
            body = ";\r\n";
        else
            body = body + "\r\n";
        if (params == null) params = "";
        else params = params.trim ();

        String fullFunctionText = "function "+funcname+" (" + params + ") {\n" + body + "\n}";

        EcmaScript parser;
        StringReader is;

        // Special case for empty parameters
        if (params.length()==0) {
            fpl = new ASTFormalParameterList(JJTFORMALPARAMETERLIST);
        } else {
            is = new java.io.StringReader(params);
            parser = new EcmaScript(is);
            try {
                fpl = (ASTFormalParameterList) parser.FormalParameterList();
                is.close();
            } catch (ParseException x) {
                throw new EcmaScriptParseException (x, new StringEvaluationSource(fullFunctionText, null));
            }
        }
        // this is very very very strange: without the toString, lots of obscure exceptions
        // deep inside the parser...
        is = new java.io.StringReader(body.toString ());
        try {
            parser = new EcmaScript (is);
            sl = (ASTStatementList) parser.StatementList();
            is.close();
        } catch (ParseException x) {
            x.printStackTrace ();
            throw new EcmaScriptParseException (x, new StringEvaluationSource(fullFunctionText, null));
        } catch (Exception x) {
            x.printStackTrace ();
            throw new RuntimeException (x.getMessage ());
        }

        FunctionEvaluationSource fes = new FunctionEvaluationSource (
        new StringEvaluationSource(fullFunctionText,null), funcname);

        DbMapping dbmap = null;

        int size = app.allThreads.size ();
        for (int i=0; i<size; i++) {
            RequestEvaluator reval = (RequestEvaluator) app.allThreads.elementAt (i);

            ObjectPrototype op = reval.getPrototype (protoname);

            EcmaScriptVariableVisitor vdvisitor = reval.evaluator.getVarDeclarationVisitor();
            Vector vnames = vdvisitor.processVariableDeclarations(sl, fes);

            FunctionPrototype fp = ConstructedFunctionObject.makeNewConstructedFunction(reval.evaluator, funcname, fes, fullFunctionText, fpl.getArguments(), vnames, sl);
            op.putHiddenProperty (funcname, fp);
        }
    }


    protected void generateErrorFeedback (String funcname, String message, String protoname)
		throws EcmaScriptException {
       int size = app.allThreads.size ();

        for (int i=0; i<size; i++) {
            RequestEvaluator reval = (RequestEvaluator) app.allThreads.elementAt (i);

            ObjectPrototype op = reval.getPrototype (protoname);

            FunctionPrototype fp = (FunctionPrototype) reval.evaluator.getFunctionPrototype ();
            FunctionPrototype func = new ThrowException (funcname, reval.evaluator, fp, message);
            op.putHiddenProperty (funcname, func);

        }
    }

    class ThrowException extends BuiltinFunctionObject {
        String message;
        ThrowException (String name, Evaluator evaluator, FunctionPrototype fp, String message) {
            super (fp, evaluator, name, 1);
            this.message = message == null ? "No error message available" : message;
        }
        public ESValue callFunction (ESObject thisObject, ESValue[] arguments) throws EcmaScriptException {
            throw new EcmaScriptException (message);
        }
        public ESObject doConstruct (ESObject thisObject, ESValue[] arguments) throws EcmaScriptException {
            throw new EcmaScriptException (message);
        }
    }


}

































































































