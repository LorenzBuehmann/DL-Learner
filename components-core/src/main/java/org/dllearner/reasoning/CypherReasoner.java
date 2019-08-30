package org.dllearner.reasoning;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.core.*;
import org.dllearner.kb.Neo4JKS;
import org.dllearner.kb.OWLAPIOntology;
import org.dllearner.learningproblems.ClassLearningProblem;
import org.dllearner.refinementoperators.RhoDRDown;
import org.neo4j.driver.v1.*;
import org.neo4j.driver.v1.types.Node;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLClassExpressionVisitorAdapter;

import static org.neo4j.driver.v1.Values.parameters;

/**
 * @author Lorenz Buehmann
 */
@ComponentAnn(name = "Cypher Reasoner", shortName = "cypr", version = 0.1)
public class CypherReasoner extends AbstractReasonerComponent {

    private final Neo4JKS ks;
    private Driver driver;

    public CypherReasoner(Neo4JKS ks) {
        this.ks = ks;

        driver = ks.getDriver();
    }

    @Override
    public ReasonerType getReasonerType() {
        return null;
    }

    @Override
    public void releaseKB() {

    }

    @Override
    public OWLDatatype getDatatype(OWLDataProperty dp) {
        return null;
    }

    @Override
    public void setSynchronized() {

    }

    @Override
    public SortedSet<OWLIndividual> getIndividuals() {
        return null;
    }

    @Override
    public String getBaseURI() {
        return null;
    }

    @Override
    public Map<String, String> getPrefixes() {
        return null;
    }

    @Override
    public void init() throws ComponentInitException {

    }


    final String CLASSES_TEMPLATE = "MATCH (cls:Class) RETURN cls.uri";
    final String OP_TEMPLATE = "MATCH (p:Relationship) RETURN p.uri";
    final String DP_TEMPLATE = "MATCH (p:Property) RETURN p.uri";
    final String DP_BOOLEAN_TEMPLATE = "MATCH (p:Property)-[:RANGE]->(ran:Resource {uri: 'http://www.w3.org/2001/XMLSchema#boolean'}) RETURN p.uri";
    final String DP_STRING_TEMPLATE = "MATCH (p:Property)-[:RANGE]->(ran:Resource {uri: 'http://www.w3.org/2001/XMLSchema#string'}) RETURN p.uri";
    final String DP_INT_TEMPLATE = "MATCH (p:Property)-[:RANGE]->(ran:Resource ) " +
            "WHERE ran.uri IN ['http://www.w3.org/2001/XMLSchema#int', 'http://www.w3.org/2001/XMLSchema#integer'] RETURN p.uri";
    final String DP_DOUBLE_TEMPLATE = "MATCH (p:Property)-[:RANGE]->(ran:Resource ) " +
            "WHERE ran.uri IN ['http://www.w3.org/2001/XMLSchema#double', 'http://www.w3.org/2001/XMLSchema#float'] RETURN p.uri";



//    final String SCO_TEMPLATE = "MATCH (sub:Class)-[:SCO]->(sup:Class {uri: $uri}) return sub.uri";
    final String SCO_TEMPLATE = "MATCH (sub:Class)-[:SCO]->(sup {uri: $uri}) return sub.uri";
    // a bit tricky because depending on the data loaded an edge (cls -rdfs:subClassOf-> owl:Thing) might exist or not
//    final String ROOT_CLASSES_TEMPLATE = "MATCH (cls:Class)-[:SCO]->(sup {uri: $uri})\n" +
//            "RETURN cls.uri\n" +
//            "UNION \n" +
//            "MATCH (cls:Class) \n" +
//            " WHERE NOT (cls)-[:SCO]->()\n" +
//            " RETURN cls.uri";
    final String ROOT_CLASSES_TEMPLATE = "MATCH (sub:Class)-[:SCO]->(sup {uri: $uri})\n" +
            "RETURN sub.uri\n" +
            "UNION \n" +
            "MATCH (sub:Class) \n" +
            " WHERE NOT (sub)-[:SCO]->()\n" +
            " RETURN sub.uri";

    final String SUPERCLASSES_TEMPLATE = "MATCH (sub:Class {uri: $uri})-[:SCO]->(sup:Class) return sup.uri";
    final String IS_SCO_TEMPLATE = "MATCH (sub:Class {uri: $sub_uri})-[:SCO]->(sup:Class {uri: $sup_uri}) return sub.uri";
    final String SPO_OP_TEMPLATE = "MATCH (sub:Relationship)-[:SPO]->(sup:Relationship {uri: $uri}) return sub.uri";
    final String SUPERPROPERTIES_OP_TEMPLATE = "MATCH (sub:Relationship {uri: $uri})-[:SPO]->(sup:Relationship) return sup.uri";
    final String SPO_DP_TEMPLATE = "MATCH (sub:Property)-[:SPO]->(sup:Property {uri: $uri}) return sub.uri";
    final String SUPERPROPERTIES_DP_TEMPLATE = "MATCH (sub:Property {uri: $uri})-[:SPO]->(sup:Property) return sup.uri";
    final String DOMAIN_OP_TEMPLATE = "MATCH (p:Relationship {uri: $uri})-[:DOMAIN]->(dom:Class ) return dom.uri";
    final String DOMAIN_DP_TEMPLATE = "MATCH (p:Property {uri: $uri})-[:DOMAIN]->(dom:Class) return dom.uri";
    final String RANGE_OP_TEMPLATE = "MATCH (p:Relationship {uri: $uri})-[:RANGE]->(ran:Class) return ran.uri";
    final String RANGE_DP_TEMPLATE = "MATCH (p:Property {uri: $uri})-[:RANGE]->(ran:Resource) return ran.uri";

    final String OP_MEMBERS = "MATCH (s:Resource)-[r: `%s`]->(o:Resource) RETURN s.uri, o.uri";
    final String DP_MEMBERS = "MATCH (s:Resource) WHERE EXISTS (s.{uri: $uri}) RETURN s";

    @Override
    public Set<OWLClass> getClasses() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(CLASSES_TEMPLATE);
                return result.stream()
                        .map(r -> df.getOWLClass(IRI.create(r.get("cls.uri").asString())))
                        .collect(Collectors.toSet());
            });
        }
    }

    @Override
    protected Set<OWLObjectProperty> getObjectPropertiesImpl() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(OP_TEMPLATE);
                return result.stream()
                        .map(r -> df.getOWLObjectProperty(IRI.create(r.get("p.uri").asString())))
                        .collect(Collectors.toSet());
            });
        }
    }

    @Override
    protected Set<OWLDataProperty> getDatatypePropertiesImpl() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DP_TEMPLATE);
                return result.stream()
                        .map(r -> df.getOWLDataProperty(IRI.create(r.get("p.uri").asString())))
                        .collect(Collectors.toSet());
            });
        }
    }

    @Override
    protected Set<OWLDataProperty> getBooleanDatatypePropertiesImpl() throws ReasoningMethodUnsupportedException {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DP_BOOLEAN_TEMPLATE);
                return result.stream()
                        .map(r -> df.getOWLDataProperty(IRI.create(r.get("p.uri").asString())))
                        .collect(Collectors.toSet());
            });
        }
    }

    @Override
    protected Set<OWLDataProperty> getStringDatatypePropertiesImpl() throws ReasoningMethodUnsupportedException {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DP_STRING_TEMPLATE);
                return result.stream()
                        .map(r -> df.getOWLDataProperty(IRI.create(r.get("p.uri").asString())))
                        .collect(Collectors.toSet());
            });
        }
    }

    @Override
    protected Set<OWLDataProperty> getIntDatatypePropertiesImpl() throws ReasoningMethodUnsupportedException {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DP_INT_TEMPLATE);
                return result.stream()
                        .map(r -> df.getOWLDataProperty(IRI.create(r.get("p.uri").asString())))
                        .collect(Collectors.toSet());
            });
        }
    }

    @Override
    protected Set<OWLDataProperty> getDoubleDatatypePropertiesImpl() throws ReasoningMethodUnsupportedException {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DP_DOUBLE_TEMPLATE);
                return result.stream()
                        .map(r -> df.getOWLDataProperty(IRI.create(r.get("p.uri").asString())))
                        .collect(Collectors.toSet());
            });
        }
    }

    @Override
    protected SortedSet<OWLClassExpression> getSubClassesImpl(OWLClassExpression ce) {
        if(ce.isAnonymous()) {
            throw new UnsupportedOperationException("anonymous classes not supported in subclasses query");
        } else if(ce.isOWLNothing()) {
            return Collections.emptySortedSet();
        }
        try (Session session = driver.session()) {
            return session.readTransaction((TransactionWork<SortedSet<OWLClassExpression>>) tx -> {
                StatementResult result = ce.isOWLThing()
                        ? tx.run(ROOT_CLASSES_TEMPLATE,
                                    parameters("uri", ce.asOWLClass().toStringID()))
                        : tx.run(SCO_TEMPLATE,
                                    parameters("uri", ce.asOWLClass().toStringID()));

                return result.stream()
                        .map(r -> df.getOWLClass(IRI.create(r.get("sub.uri").asString())))
                        .collect(Collectors.toCollection(TreeSet::new));
            });
        }
    }

    @Override
    protected SortedSet<OWLClassExpression> getSuperClassesImpl(OWLClassExpression ce) {
        if(ce.isAnonymous()) {
            throw new UnsupportedOperationException("anonymous classes not supported in superclasses query");
        }
        try (Session session = driver.session()) {
            return session.readTransaction((TransactionWork<SortedSet<OWLClassExpression>>) tx -> {
                StatementResult result = tx.run(SUPERCLASSES_TEMPLATE,
                        parameters("uri", ce.asOWLClass().toStringID()));
                return result.stream()
                        .map(r -> df.getOWLClass(IRI.create(r.get("sup.uri").asString())))
                        .collect(Collectors.toCollection(TreeSet::new));
            });
        }
    }

    @Override
    protected boolean isSuperClassOfImpl(OWLClassExpression superclass, OWLClassExpression subclass) {
        if(superclass.isAnonymous() || subclass.isAnonymous()) {
            throw new UnsupportedOperationException("anonymous classes not supported in is_superclass_of query");
        }
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(IS_SCO_TEMPLATE,
                        parameters("sub_uri", subclass.asOWLClass().toStringID(),
                        "sup_uri", superclass.asOWLClass().toStringID()));
                return result.hasNext();
            });
        }
    }

    @Override
    protected SortedSet<OWLObjectProperty> getSubPropertiesImpl(OWLObjectProperty p) {
        try (Session session = driver.session()) {
            return session.readTransaction((TransactionWork<SortedSet<OWLObjectProperty>>) tx -> {
                StatementResult result = tx.run(SPO_OP_TEMPLATE,
                        parameters("uri", p.toStringID()));
                return result.stream()
                        .map(r -> df.getOWLObjectProperty(IRI.create(r.get("sub.uri").asString())))
                        .collect(Collectors.toCollection(TreeSet::new));
            });
        }
    }

    @Override
    protected SortedSet<OWLObjectProperty> getSuperPropertiesImpl(OWLObjectProperty p) throws ReasoningMethodUnsupportedException {
        try (Session session = driver.session()) {
            return session.readTransaction((TransactionWork<SortedSet<OWLObjectProperty>>) tx -> {
                StatementResult result = tx.run(SUPERPROPERTIES_OP_TEMPLATE,
                        parameters("uri", p.toStringID()));
                return result.stream()
                        .map(r -> df.getOWLObjectProperty(IRI.create(r.get("sup.uri").asString())))
                        .collect(Collectors.toCollection(TreeSet::new));
            });
        }
    }

    @Override
    protected SortedSet<OWLDataProperty> getSubPropertiesImpl(OWLDataProperty p) {
        try (Session session = driver.session()) {
            return session.readTransaction((TransactionWork<SortedSet<OWLDataProperty>>) tx -> {
                StatementResult result = tx.run(SPO_DP_TEMPLATE,
                        parameters("uri", p.toStringID()));
                return result.stream()
                        .map(r -> df.getOWLDataProperty(IRI.create(r.get("sub.uri").asString())))
                        .collect(Collectors.toCollection(TreeSet::new));
            });
        }
    }

    @Override
    protected SortedSet<OWLDataProperty> getSuperPropertiesImpl(OWLDataProperty p) throws ReasoningMethodUnsupportedException {
        try (Session session = driver.session()) {
            return session.readTransaction((TransactionWork<SortedSet<OWLDataProperty>>) tx -> {
                StatementResult result = tx.run(SUPERPROPERTIES_DP_TEMPLATE,
                        parameters("uri", p.toStringID()));
                return result.stream()
                        .map(r -> df.getOWLDataProperty(IRI.create(r.get("sup.uri").asString())))
                        .collect(Collectors.toCollection(TreeSet::new));
            });
        }
    }

    @Override
    protected OWLClassExpression getDomainImpl(OWLObjectProperty p) {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DOMAIN_OP_TEMPLATE,
                        parameters("uri", p.toStringID()));
                Set<OWLClass> domains = result.stream()
                        .map(r -> df.getOWLClass(IRI.create(r.get("dom.uri").asString())))
                        .collect(Collectors.toSet());
                OWLClassExpression domain;
                if(domains.isEmpty()) {
                    domain = df.getOWLThing();
                } else if(domains.size() == 1) {
                    domain = domains.iterator().next();
                } else {
                    domain = df.getOWLObjectIntersectionOf(domains);
                }
                return domain;
            });
        }
    }

    @Override
    protected OWLClassExpression getDomainImpl(OWLDataProperty p) {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DOMAIN_DP_TEMPLATE,
                        parameters("uri", p.toStringID()));
                Set<OWLClass> domains = result.stream()
                        .map(r -> df.getOWLClass(IRI.create(r.get("dom.uri").asString())))
                        .collect(Collectors.toSet());
                OWLClassExpression domain;
                if(domains.isEmpty()) {
                    domain = df.getOWLThing();
                } else if(domains.size() == 1) {
                    domain = domains.iterator().next();
                } else {
                    domain = df.getOWLObjectIntersectionOf(domains);
                }
                return domain;
            });
        }
    }

    @Override
    protected OWLClassExpression getRangeImpl(OWLObjectProperty p) {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(RANGE_OP_TEMPLATE,
                        parameters("uri", p.toStringID()));
                Set<OWLClass> domains = result.stream()
                        .map(r -> df.getOWLClass(IRI.create(r.get("ran.uri").asString())))
                        .collect(Collectors.toSet());
                OWLClassExpression domain;
                if(domains.isEmpty()) {
                    domain = df.getOWLThing();
                } else if(domains.size() == 1) {
                    domain = domains.iterator().next();
                } else {
                    domain = df.getOWLObjectIntersectionOf(domains);
                }
                return domain;
            });
        }
    }

    @Override
    protected OWLDataRange getRangeImpl(OWLDataProperty p) {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(RANGE_DP_TEMPLATE,
                        parameters("uri", p.toStringID()));
               if(result.hasNext()) {
                   return df.getOWLDatatype(IRI.create(result.next().get("ran.uri").asString()));
               }

               return null;
            });
        }
    }

    /*************************************
     * ABox queries                      *
     *************************************/

    @Override
    protected SortedSet<OWLIndividual> getIndividualsImpl(OWLClassExpression ce) {
        String query = new OWLClassExpressionToCypherConverter().convert(ce);
//        System.out.println(ce + ":::" + query);
        try (Session session = driver.session()) {
            return session.readTransaction((TransactionWork<SortedSet<OWLIndividual>>) tx -> {
                StatementResult result = tx.run(query);
                return result.stream().map(r -> df.getOWLNamedIndividual(IRI.create(r.get("n0").asNode().get("uri").asString())))
                        .collect(Collectors.toCollection(TreeSet::new));
            });
        }
    }

    @Override
    protected Map<OWLIndividual, SortedSet<OWLIndividual>> getPropertyMembersImpl(OWLObjectProperty p) {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(String.format(OP_MEMBERS, p.toStringID()));

                return result.stream()
                        .collect(Collectors.groupingBy(r -> df.getOWLNamedIndividual(IRI.create(r.get("s.uri").asString())),
                                    Collectors.mapping(r -> df.getOWLNamedIndividual(IRI.create(r.get("o.uri").asString())),
                                    Collectors.toCollection(TreeSet::new))));
                });
            }
    }

    class OWLClassExpressionToCypherConverter extends OWLClassExpressionVisitorAdapter {
        String query;

        int cnt = 0;
        int depth = 0;
        Map<Integer, Map<OWLClass, String>> depthToNodeIDMapping = new HashMap<>();

        Deque<String> currentNode = new ArrayDeque<>();
        String targetNode;

        private String nextNode() {
            return "n" + cnt++;
        }

        public String convert(OWLClassExpression ce) {
            query = "MATCH ";
            targetNode = nextNode();
            currentNode.push(targetNode);
            ce.accept(this);
            query += " RETURN " + targetNode;
//            System.out.println(query);
            return query;
        }

        @Override
        public void visit(OWLClass ce) {
            query += "(" + currentNode.peek() + ":`" + ce.toStringID() + "`)";
        }

        @Override
        public void visit(OWLObjectSomeValuesFrom ce) {
           query += "(" + currentNode.peek() + ")" + "-[:`" + ce.getProperty().asOWLObjectProperty().toStringID() + "`]->";
           depth++;
           currentNode.push(nextNode());
           ce.getFiller().accept(this);
           depth--;
           currentNode.pop();
        }

        @Override
        public void visit(OWLObjectIntersectionOf ce) {
            Set<OWLClassExpression> operands = ce.getOperands();

            // we process the classes first in order to create a single node with the labels here
            query += "(" + currentNode.peek();
            operands.stream().filter(op -> !op.isAnonymous()).map(OWLClassExpression::asOWLClass).forEach(cls -> {
                query += ":" + "`" + cls.toStringID() + "`";
            });
            query += ")";

            operands.stream().filter(OWLClassExpression::isAnonymous).forEach(op -> {
                query += ",";
                op.accept(this);
            });
        }
    }

    public static void main(String[] args) throws Exception {
        OWLDataFactory df = OWLManager.getOWLDataFactory();

        try(Neo4JKS ks = new Neo4JKS("bolt://localhost:7687", "neo4j", "123pw")) {

            ks.init();

            CypherReasoner reasoner = new CypherReasoner(ks);
            reasoner.setPrecomputeClassHierarchy(false);
            reasoner.setPrecomputeDataPropertyHierarchy(false);
            reasoner.setPrecomputeObjectPropertyHierarchy(false);
            reasoner.init();

            System.out.println(reasoner.getClasses());
            System.out.println(reasoner.getObjectProperties());
            System.out.println(reasoner.getDatatypeProperties());

            SortedSet<OWLClassExpression> subClasses = reasoner.getSubClasses(df.getOWLClass(IRI.create("http://ns.softwiki.de/req/Requirement")));
            System.out.println(subClasses);

            SortedSet<OWLObjectProperty> subProperties = reasoner.getSubProperties(df.getOWLObjectProperty(IRI.create("http://ns.softwiki.de/req/undirectedrelation")));
            System.out.println(subProperties);

            OWLClassExpression domain = reasoner.getDomain(df.getOWLObjectProperty(IRI.create("http://ns.softwiki.de/req/details")));
            System.out.println(domain);

            OWLClassExpression range = reasoner.getRange(df.getOWLObjectProperty(IRI.create("http://ns.softwiki.de/req/details")));
            System.out.println(range);

            SortedSet<OWLDataProperty> subPropertiesDP = reasoner.getSubProperties(df.getOWLDataProperty(IRI.create("http://ns.softwiki.de/req/averageRate")));
            System.out.println(subPropertiesDP);

            OWLClassExpression domainDP = reasoner.getDomain(df.getOWLDataProperty(IRI.create("http://ns.softwiki.de/req/creationDate")));
            System.out.println(domainDP);

            OWLDataRange rangeDP = reasoner.getRange(df.getOWLDataProperty(IRI.create("http://ns.softwiki.de/req/creationDate")));
            System.out.println(rangeDP);

            OWLClassExpression ce = df.getOWLObjectIntersectionOf(
                    df.getOWLClass(IRI.create("http://ns.softwiki.de/req/CustomerRequirement")),
                    df.getOWLObjectSomeValuesFrom(
                            df.getOWLObjectProperty(IRI.create("http://ns.softwiki.de/req/isCreatedBy")),
                            df.getOWLClass(IRI.create("http://ns.softwiki.de/req/Customer"))
                    )
            );
            SortedSet<OWLIndividual> individuals = reasoner.getIndividuals(ce);
            System.out.println(individuals);



            ClassLearningProblem lp = new ClassLearningProblem(reasoner);
            lp.setClassToDescribe(df.getOWLClass(IRI.create("http://ns.softwiki.de/req/CustomerRequirement")));
            lp.init();

            RhoDRDown op = new RhoDRDown();
            op.setReasoner(reasoner);
            op.setUseNegation(false);
            op.setUseHasValueConstructor(false);
            op.setUseCardinalityRestrictions(false);
            op.setUseExistsConstructor(true);
            op.setUseAllConstructor(false);
            op.init();

            CELOE alg = new CELOE(lp, reasoner);
            alg.setMaxExecutionTimeInSeconds(10);
            alg.setOperator(op);
            alg.setWriteSearchTree(true);
            alg.init();

            alg.start();

        }

    }
}
