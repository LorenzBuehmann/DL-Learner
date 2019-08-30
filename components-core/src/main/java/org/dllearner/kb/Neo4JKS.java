package org.dllearner.kb;

import org.dllearner.core.ComponentInitException;
import org.dllearner.core.KnowledgeSource;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;

/**
 * @author Lorenz Buehmann
 */
public class Neo4JKS implements KnowledgeSource, AutoCloseable {

    private final Driver driver;

    public Neo4JKS(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
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
