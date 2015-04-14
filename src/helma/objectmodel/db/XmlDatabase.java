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

package helma.objectmodel.db;

import helma.objectmodel.*;
import helma.objectmodel.dom.*;
import java.io.*;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 * A simple XML-database
 */
public final class XmlDatabase implements IDatabase {
    private String dbHome;
    private File dbBaseDir;
    private NodeManager nmgr;
    private IDGenerator idgen;

    // character encoding to use when writing files.
    // use standard encoding by default.
    private String encoding = null;

    /**
     * Creates a new XmlDatabase object.
     *
     * @param dbHome ...
     * @param dbFilename ...
     * @param nmgr ...
     *
     * @throws DatabaseException ...
     * @throws RuntimeException ...
     */
    public XmlDatabase(String dbHome, String dbFilename, NodeManager nmgr)
                throws DatabaseException {
        this.dbHome = dbHome;
        this.nmgr = nmgr;
        dbBaseDir = new File(dbHome);

        if (!dbBaseDir.exists() && !dbBaseDir.mkdirs()) {
            throw new RuntimeException("Couldn't create DB-directory");
        }

        this.encoding = nmgr.app.getCharset();
    }

    /**
     *
     */
    public void shutdown() {
    }

    /**
     *
     *
     * @return ...
     *
     * @throws DatabaseException ...
     */
    public ITransaction beginTransaction() throws DatabaseException {
        return null;
    }

    /**
     *
     *
     * @param txn ...
     *
     * @throws DatabaseException ...
     */
    public void commitTransaction(ITransaction txn) throws DatabaseException {
    }

    /**
     *
     *
     * @param txn ...
     *
     * @throws DatabaseException ...
     */
    public void abortTransaction(ITransaction txn) throws DatabaseException {
    }

    /**
     *
     *
     * @return ...
     *
     * @throws ObjectNotFoundException ...
     */
    public String nextID() throws ObjectNotFoundException {
        if (idgen == null) {
            getIDGenerator(null);
        }

        return idgen.newID();
    }

    /**
     *
     *
     * @param txn ...
     *
     * @return ...
     *
     * @throws ObjectNotFoundException ...
     */
    public IDGenerator getIDGenerator(ITransaction txn)
                               throws ObjectNotFoundException {
        File file = new File(dbBaseDir, "idgen.xml");

        this.idgen = IDGenParser.getIDGenerator(file);

        return idgen;
    }

    /**
     *
     *
     * @param txn ...
     * @param idgen ...
     *
     * @throws Exception ...
     */
    public void saveIDGenerator(ITransaction txn, IDGenerator idgen)
                         throws IOException {
        File file = new File(dbBaseDir, "idgen.xml");

        IDGenParser.saveIDGenerator(idgen, file);
        this.idgen = idgen;
    }

    /**
     *
     *
     * @param txn ...
     * @param kstr ...
     *
     * @return ...
     *
     * @throws Exception ...
     * @throws ObjectNotFoundException ...
     */
    public INode getNode(ITransaction txn, String kstr)
                  throws IOException, ObjectNotFoundException,
                         ParserConfigurationException, SAXException {
        File f = new File(dbBaseDir, kstr + ".xml");

        if (!f.exists()) {
            throw new ObjectNotFoundException("Object not found for key " + kstr + ".");
        }

        try {
            XmlDatabaseReader reader = new XmlDatabaseReader(nmgr);
            Node node = reader.read(f);
            node.markAs(INodeState.CLEAN);

            return node;
        } catch (RuntimeException x) {
            nmgr.app.logEvent("error reading node from XmlDatbase: " + x.toString());
            throw new ObjectNotFoundException(x.toString());
        }
    }

    /**
     *
     *
     * @param txn ...
     * @param kstr ...
     * @param node ...
     *
     * @throws Exception ...
     */
    public void saveNode(ITransaction txn, String kstr, INode node)
                  throws IOException {
        XmlWriter writer = null;
        File file = new File(dbBaseDir, kstr + ".xml");

        if (encoding != null) {
            writer = new XmlWriter(file, encoding);
        } else {
            writer = new XmlWriter(file);
        }

        writer.setMaxLevels(1);

        writer.write((Node) node);
        ((Node) node).markAs(INodeState.CLEAN);

        writer.close();
    }

    /**
     *
     *
     * @param txn ...
     * @param kstr ...
     *
     * @throws Exception ...
     */
    public void deleteNode(ITransaction txn, String kstr)
                    throws IOException {
        File f = new File(dbBaseDir, kstr + ".xml");

        f.delete();
    }

    /**
     *
     *
     * @param enc ...
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    /**
     *
     *
     * @return ...
     */
    public String getEncoding() {
        return encoding;
    }
}
