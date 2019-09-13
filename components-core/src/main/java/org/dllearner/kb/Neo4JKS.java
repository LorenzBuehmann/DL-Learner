package org.dllearner.kb;

import org.dllearner.core.ComponentInitException;
import org.dllearner.core.KnowledgeSource;
import org.neo4j.driver.v1.AuthToken;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;

/**
 * A knowledge source backed by a Neo4j database instance.
 *
 * @author Lorenz Buehmann
 */
public class Neo4JKS implements KnowledgeSource, AutoCloseable {

    private final Driver driver;

    /**
     *
     * @param uri the server URI
     * @param user the username
     * @param password the password
     */
    public Neo4JKS(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    /**
     * @param uri the connection URI
     * @param authToken the authentification token
     */
    public Neo4JKS(String uri, AuthToken authToken) {
        driver = GraphDatabase.driver(uri, authToken);
    }

    /**
     *
     * @param driver the Neo4J database driver
     */
    public Neo4JKS(Driver driver) {
        this.driver = driver;
    }

    @Override
    public void init() throws ComponentInitException {
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }

    public Driver getDriver() {
        return driver;
    }
}
