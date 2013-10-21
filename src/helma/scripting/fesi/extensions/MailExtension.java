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

package helma.scripting.fesi.extensions;

import FESI.Data.*;
import FESI.Exceptions.*;
import FESI.Extensions.*;
import FESI.Interpreter.*;
import helma.util.*;
import java.io.*;
import java.util.*;

/**
 * Extension to create and send mail messages via SMTP from HOP applications
 */
public class MailExtension extends Extension {
    protected Evaluator eval = null;
    protected ObjectPrototype esMailPrototype = null;
    protected Properties mprops;

    /**
     * Creates a new MailExtension object.
     */
    public MailExtension() {
        super();
    }

    /**
     *
     *
     * @param props ...
     */
    public void setProperties(Properties props) {
        this.mprops = props;
    }

    /**
     * Called by the evaluator after the extension is loaded.
     */
    public void initializeExtension(Evaluator evaluator)
                             throws EcmaScriptException {
        this.eval = evaluator;

        GlobalObject go = evaluator.getGlobalObject();
        FunctionPrototype fp = (FunctionPrototype) evaluator.getFunctionPrototype();

        ESObject op = evaluator.getObjectPrototype();

        esMailPrototype = new ObjectPrototype(op, evaluator); // the Node prototype

        ESObject mail = new GlobalObjectMail("Mail", evaluator, fp, this); // the Mail constructor

        go.putHiddenProperty("Mail", mail); // register the constructor for a Mail object.

        // methods for sending mail from JS...
        ESObject p = new MailSetText("setText", evaluator, fp);

        esMailPrototype.putHiddenProperty("setText", p);
        esMailPrototype.putHiddenProperty("addText", p);

        esMailPrototype.putHiddenProperty("addPart",
                                          new MailAddPart("addPart", evaluator, fp));
        esMailPrototype.putHiddenProperty("setSubject",
                                          new MailSetSubject("setSubject", evaluator, fp));
        esMailPrototype.putHiddenProperty("setReplyTo",
                                          new MailSetReplyTo("setReplyTo", evaluator, fp));
        esMailPrototype.putHiddenProperty("setFrom",
                                          new MailSetFrom("setFrom", evaluator, fp));

        esMailPrototype.putHiddenProperty("setHeader",
                                          new MailSetHeader("setHeader", evaluator, fp));
        esMailPrototype.putHiddenProperty("addHeader",
                                          new MailAddHeader("addHeader", evaluator, fp));
        esMailPrototype.putHiddenProperty("removeHeader",
                                          new MailRemoveHeader("removeHeader", evaluator, fp));
        esMailPrototype.putHiddenProperty("setSentDate",
                                          new MailSetSentDate("setSentDate", evaluator, fp));

        p = new MailAddTo("addTo", evaluator, fp);
        esMailPrototype.putHiddenProperty("addTo", p);
        esMailPrototype.putHiddenProperty("setTo", p);

        p = new MailAddCC("addCC", evaluator, fp);
        esMailPrototype.putHiddenProperty("addCC", p);
        esMailPrototype.putHiddenProperty("setCC", p);

        p = new MailAddBCC("addBCC", evaluator, fp);
        esMailPrototype.putHiddenProperty("addBCC", p);
        esMailPrototype.putHiddenProperty("setBCC", p);

        esMailPrototype.putHiddenProperty("send", new MailSend("send", evaluator, fp));
    }

    class GlobalObjectMail extends BuiltinFunctionObject {
        MailExtension mailx;

        GlobalObjectMail(String name, Evaluator evaluator, FunctionPrototype fp,
                         MailExtension mailx) {
            super(fp, evaluator, name, 1);
            this.mailx = mailx;
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            return doConstruct(thisObject, arguments);
        }

        public ESObject doConstruct(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = null;

            if (arguments.length == 0) {
                mail = new ESMail(mailx);
            } else {
                mail = new ESMail(mailx);

                // should/could do something with extra arguments...
            }

            return mail;
        }
    }

    class MailSetText extends BuiltinFunctionObject {
        MailSetText(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            if (arguments.length == 1) {
                try {
                    mail.setText(arguments[0]);
                } catch (Exception x) {
                    mail.setStatus(ESMail.TEXT);

                    return ESBoolean.makeBoolean(false);
                }
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailAddPart extends BuiltinFunctionObject {
        MailAddPart(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            try {
                mail.addPart(arguments);
            } catch (Exception x) {
                mail.setStatus(ESMail.MIMEPART);

                return ESBoolean.makeBoolean(false);
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailSetSentDate extends BuiltinFunctionObject {
        MailSetSentDate(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            if (arguments.length == 1) {
                try {
                    mail.setSentDate(arguments[0]);
                } catch (Exception x) {
                    mail.setStatus(ESMail.SENTDATE);

                    return ESBoolean.makeBoolean(false);
                }
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailSetHeader extends BuiltinFunctionObject {
        MailSetHeader(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            if (arguments.length == 2) {
                try {
                    mail.setHeader(arguments[0], arguments[1]);
                } catch (Exception x) {
                    mail.setStatus(ESMail.HEADER);

                    return ESBoolean.makeBoolean(false);
                }
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailAddHeader extends BuiltinFunctionObject {
        MailAddHeader(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            if (arguments.length == 2) {
                try {
                    mail.addHeader(arguments[0], arguments[1]);
                } catch (Exception x) {
                    mail.setStatus(ESMail.HEADER);

                    return ESBoolean.makeBoolean(false);
                }
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailRemoveHeader extends BuiltinFunctionObject {
        MailRemoveHeader(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            if (arguments.length == 1) {
                try {
                    mail.removeHeader(arguments[0]);
                } catch (Exception x) {
                    mail.setStatus(ESMail.HEADER);

                    return ESBoolean.makeBoolean(false);
                }
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailSetSubject extends BuiltinFunctionObject {
        MailSetSubject(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            if (arguments.length == 1) {
                try {
                    mail.setSubject(arguments[0]);
                } catch (Exception x) {
                    mail.setStatus(ESMail.SUBJECT);

                    return ESBoolean.makeBoolean(false);
                }
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailSetReplyTo extends BuiltinFunctionObject {
        MailSetReplyTo(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            if (arguments.length == 1) {
                try {
                    mail.setReplyTo(arguments[0]);
                } catch (Exception x) {
                    mail.setStatus(ESMail.REPLYTO);

                    return ESBoolean.makeBoolean(false);
                }
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailSetFrom extends BuiltinFunctionObject {
        MailSetFrom(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            try {
                mail.setFrom(arguments);
            } catch (Exception x) {
                mail.setStatus(ESMail.FROM);

                return ESBoolean.makeBoolean(false);
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailAddTo extends BuiltinFunctionObject {
        MailAddTo(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            try {
                mail.addTo(arguments);
            } catch (Exception x) {
                mail.setStatus(ESMail.TO);

                return ESBoolean.makeBoolean(false);
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailAddCC extends BuiltinFunctionObject {
        MailAddCC(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            try {
                mail.addCC(arguments);
            } catch (Exception x) {
                mail.setStatus(ESMail.CC);

                return ESBoolean.makeBoolean(false);
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailAddBCC extends BuiltinFunctionObject {
        MailAddBCC(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            try {
                mail.addBCC(arguments);
            } catch (Exception x) {
                mail.setStatus(ESMail.BCC);

                return ESBoolean.makeBoolean(false);
            }

            return ESBoolean.makeBoolean(true);
        }
    }

    class MailSend extends BuiltinFunctionObject {
        MailSend(String name, Evaluator evaluator, FunctionPrototype fp) {
            super(fp, evaluator, name, 1);
        }

        public ESValue callFunction(ESObject thisObject, ESValue[] arguments)
                             throws EcmaScriptException {
            ESMail mail = (ESMail) thisObject;

            try {
                mail.send();
            } catch (Exception x) {
                evaluator.engine.getApplication().logEvent("Error sending mail: " + x);
                mail.setStatus(ESMail.SEND);

                return ESBoolean.makeBoolean(false);
            }

            return ESBoolean.makeBoolean(true);
        }
    }
}
