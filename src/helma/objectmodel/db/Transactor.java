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

import helma.framework.TimeoutException;
import helma.objectmodel.*;
import helma.util.Timer;
import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * A subclass of thread that keeps track of changed nodes and triggers
 * changes in the database when a transaction is commited.
 */
public class Transactor extends Thread {
    NodeManager nmgr;

    // List of nodes to be updated
    private HashMap nodes;
    private ArrayList nodesArray;

    // List of visited clean nodes
    private HashMap cleannodes;

    // Is a transaction in progress?
    private volatile boolean active;
    private volatile boolean killed;

    // Transaction for the embedded database
    protected ITransaction txn;

    // Transactions for SQL data sources
    protected HashMap sqlCon;
    public Timer timer;

    // when did the current transaction start?
    private long tstart;

    // a name to log the transaction. For HTTP transactions this is the rerquest path
    private String tname;

    /**
     * Creates a new Transactor object.
     *
     * @param runnable ...
     * @param group ...
     * @param nmgr ...
     */
    public Transactor(Runnable runnable, ThreadGroup group, NodeManager nmgr) {
        super(group, runnable, group.getName());
        this.nmgr = nmgr;
        nodes = new HashMap();
        nodesArray = new ArrayList();
        cleannodes = new HashMap();
        sqlCon = new HashMap();
        active = false;
        killed = false;
        timer = new Timer();
    }

    /**
     *
     *
     * @param node ...
     */
    public void visitNode(Node node) {
        if (node != null) {
            Key key = node.getKey();

            if (!nodes.containsKey(key)) {
                nodes.put(key, node);
                nodesArray.add(node);
            }
        }
    }

    /**
     *
     *
     * @param node ...
     */
    public void dropNode(Node node) {
        if (node != null) {
            Key key = node.getKey();

            nodes.remove(key);
            nodesArray.remove(node);
        }
    }

    /**
     *
     *
     * @param node ...
     */
    public void visitCleanNode(Node node) {
        if (node != null) {
            Key key = node.getKey();

            if (!cleannodes.containsKey(key)) {
                cleannodes.put(key, node);
            }
        }
    }

    /**
     *
     *
     * @param key ...
     * @param node ...
     */
    public void visitCleanNode(Key key, Node node) {
        if (node != null) {
            if (!cleannodes.containsKey(key)) {
                cleannodes.put(key, node);
            }
        }
    }

    /**
     *
     *
     * @param key ...
     *
     * @return ...
     */
    public Node getVisitedNode(Object key) {
        return (key == null) ? null : (Node) cleannodes.get(key);
    }

    /**
     *
     *
     * @return ...
     */
    public boolean isActive() {
        return active;
    }

    /**
     *
     *
     * @param src ...
     * @param con ...
     */
    public void registerConnection(DbSource src, Connection con) {
        sqlCon.put(src, con);
    }

    /**
     *
     *
     * @param src ...
     *
     * @return ...
     */
    public Connection getConnection(DbSource src) {
        return (Connection) sqlCon.get(src);
    }

    /**
     *
     *
     * @param tnm ...
     *
     * @throws Exception ...
     * @throws DatabaseException ...
     */
    public synchronized void begin(String tnm) throws Exception {
        if (killed) {
            throw new DatabaseException("Transaction started on killed thread");
        }

        if (active) {
            abort();
        }

        nodes.clear();
        nodesArray.clear();
        cleannodes.clear();
        txn = nmgr.db.beginTransaction();
        active = true;
        tstart = System.currentTimeMillis();
        tname = tnm;
    }

    /**
     *
     *
     * @throws Exception ...
     */
    public synchronized void commit() throws Exception {
        if (killed) {
            abort();

            return;
        }

        int ins = 0;
        int upd = 0;
        int dlt = 0;
        int l = nodesArray.size();

        Replicator replicator = nmgr.getReplicator();

        for (int i = 0; i < l; i++) {
            Node node = (Node) nodesArray.get(i);

            // update nodes in db
            int nstate = node.getState();

            if (nstate == Node.NEW) {
                nmgr.registerNode(node); // register node with nodemanager cache
                nmgr.insertNode(nmgr.db, txn, node);
                node.setState(Node.CLEAN);

                if (replicator != null) {
                    replicator.addNewNode(node);
                }

                ins++;
                nmgr.app.logEvent("inserted: Node " + node.getPrototype() + "/" +
                                  node.getID());
            } else if (nstate == Node.MODIFIED) {
                nmgr.updateNode(nmgr.db, txn, node);
                node.setState(Node.CLEAN);

                if (replicator != null) {
                    replicator.addModifiedNode(node);
                }

                upd++;
                nmgr.app.logEvent("updated: Node " + node.getPrototype() + "/" +
                                  node.getID());
            } else if (nstate == Node.DELETED) {
                // nmgr.app.logEvent ("deleted: "+node.getFullName ()+" ("+node.getName ()+")");
                nmgr.deleteNode(nmgr.db, txn, node);
                nmgr.evictNode(node);

                if (replicator != null) {
                    replicator.addDeletedNode(node);
                }

                dlt++;
            } else {
                // nmgr.app.logEvent ("noop: "+node.getFullName ());
            }

            node.clearWriteLock();
        }

        nodes.clear();
        nodesArray.clear();
        cleannodes.clear();

        if (nmgr.idgen.dirty) {
            nmgr.db.saveIDGenerator(txn, nmgr.idgen);
            nmgr.idgen.dirty = false;
        }

        if (active) {
            active = false;
            nmgr.db.commitTransaction(txn);
            txn = null;
        }

        nmgr.app.logAccess(tname + " " + l + " marked, " + ins + " inserted, " + upd +
                           " updated, " + dlt + " deleted in " +
                           (System.currentTimeMillis() - tstart) + " millis");
    }

    /**
     *
     *
     * @throws Exception ...
     */
    public synchronized void abort() throws Exception {
        int l = nodesArray.size();

        for (int i = 0; i < l; i++) {
            Node node = (Node) nodesArray.get(i);

            // Declare node as invalid, so it won't be used by other threads that want to
            // write on it and remove it from cache
            nmgr.evictNode(node);
            node.clearWriteLock();
        }

        nodes.clear();
        nodesArray.clear();
        cleannodes.clear();

        // close any JDBC connections associated with this transactor thread
        closeConnections();

        if (active) {
            active = false;

            if (txn != null) {
                nmgr.db.abortTransaction(txn);
                txn = null;
            }

            nmgr.app.logAccess(tname + " aborted after " +
                               (System.currentTimeMillis() - tstart) + " millis");
        }
    }

    /**
     *
     */
    public synchronized void kill() {
        killed = true;

        // The thread is told to stop by setting the thread flag in the EcmaScript
        // evaluator, so we can hope that it stops without doing anything else.
        try {
            join(500);
        } catch (InterruptedException ir) {
        }

        // Interrupt the thread if it has not noticed the flag (e.g. because it is busy
        // reading from a network socket).
        if (isAlive()) {
            interrupt();

            try {
                join(1000);
            } catch (InterruptedException ir) {
            }
        }
    }

    /**
     *
     */
    public void closeConnections() {
        // nmgr.app.logEvent("Cleaning up Transactor thread");
        if (sqlCon != null) {
            for (Iterator i = sqlCon.values().iterator(); i.hasNext();) {
                try {
                    Connection con = (Connection) i.next();

                    con.close();
                    nmgr.app.logEvent("Closing DB connection: " + con);
                } catch (Exception ignore) {
                }
            }

            sqlCon.clear();
        }
    }

    /**
     *
     *
     * @return ...
     */
    public String toString() {
        return "Transactor[" + this.getId() + ":" + tname + "]";
    }
}
