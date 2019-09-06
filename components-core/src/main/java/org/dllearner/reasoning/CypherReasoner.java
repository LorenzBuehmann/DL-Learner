package org.dllearner.reasoning;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import org.apache.jena.vocabulary.RDFS;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.core.*;
import org.dllearner.kb.Neo4JKS;
import org.dllearner.learningproblems.ClassLearningProblem;
import org.dllearner.refinementoperators.RhoDRDown;
import org.dllearner.utilities.OWLClassExpressionToCypherConverter;
import org.neo4j.driver.v1.*;
import org.neo4j.driver.v1.types.Node;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import static org.neo4j.driver.v1.Values.parameters;

import static java.util.stream.Collectors.*;

/**
 * A reasoner based on Cypher query language used with Neo4J graph database.
 *
 * The current implementation relies on the data model introduced by the Neosemantics plugin to load
 * OWL ontologies and RDF data:
 * - schema entities are labelled with 'Class', 'Relationship' and 'Property'
 * - schema comprises subClassOf, subPropertyOf, domain, range
 * - instance data is labelled with classes
 *
 * For some light-weight inference, we make use of stored procedures provided by the plugin:
 * - semantics.inference.nodesLabelled: get individuals of a class (s rdf:type C),(C rdfs:subClassOf D) -> (s rdf:type D)
 * - semantics.inference.getRels get individuals related by some object property (s p o),(p rdfs:subPropertyOf q) -> (s q o)
 *
 *
 * @author Lorenz Buehmann
 */
@ComponentAnn(name = "Cypher Reasoner", shortName = "cypr", version = 0.1)
public class CypherReasoner extends AbstractReasonerComponent {

    private final Neo4JKS ks;
    private Driver driver;

    private boolean useInference = false;

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

    public void setUseInference(boolean useInference) {
        this.useInference = useInference;
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

    final String ALL_INDIVIDUALS = "with ['Class', 'Relationship', 'Property'] as blacklist\n" +
            "match (n:Resource) \n" +
            "where not any (l in labels(n) where l in blacklist)  \n" +
            "return n";


    @Override
    public Set<OWLClass> getClasses() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(CLASSES_TEMPLATE);
                return result.stream()
                        .map(r -> asOWLEntity(r.get("cls.uri"), EntityType.CLASS))
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
                        .map(r -> asOWLEntity(r.get("p.uri"), EntityType.OBJECT_PROPERTY))
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
                        .map(r -> asOWLEntity(r.get("p.uri"), EntityType.DATA_PROPERTY))
                        .collect(Collectors.toSet());
            });
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends OWLEntity> E asOWLEntity(Value val, EntityType<E> entityType) {
        IRI iri = IRI.create(val.asString());
        if(entityType == EntityType.CLASS) {
            return (E) df.getOWLClass(iri);
        } else if(entityType == EntityType.OBJECT_PROPERTY) {
            return (E) df.getOWLObjectProperty(iri);
        } else if(entityType == EntityType.DATA_PROPERTY) {
            return (E) df.getOWLDataProperty(iri);
        } else if(entityType == EntityType.NAMED_INDIVIDUAL) {
            return (E) df.getOWLNamedIndividual(iri);
        } else if(entityType == EntityType.DATATYPE) {
            return (E) df.getOWLDatatype(iri);
        }
        return null;
    }

    @Override
    protected Set<OWLDataProperty> getBooleanDatatypePropertiesImpl() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DP_BOOLEAN_TEMPLATE);
                return result.stream()
                        .map(r -> asOWLEntity(r.get("p.uri"), EntityType.DATA_PROPERTY))
                        .collect(Collectors.toSet());
            });
        }
    }

    @Override
    protected Set<OWLDataProperty> getStringDatatypePropertiesImpl() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DP_STRING_TEMPLATE);
                return result.stream()
                        .map(r -> asOWLEntity(r.get("p.uri"), EntityType.DATA_PROPERTY))
                        .collect(Collectors.toSet());
            });
        }
    }

    @Override
    protected Set<OWLDataProperty> getIntDatatypePropertiesImpl() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DP_INT_TEMPLATE);
                return result.stream()
                        .map(r -> asOWLEntity(r.get("p.uri"), EntityType.DATA_PROPERTY))
                        .collect(Collectors.toSet());
            });
        }
    }

    @Override
    protected Set<OWLDataProperty> getDoubleDatatypePropertiesImpl() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DP_DOUBLE_TEMPLATE);
                return result.stream()
                        .map(r -> asOWLEntity(r.get("p.uri"), EntityType.DATA_PROPERTY))
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
                        .map(r -> asOWLEntity(r.get("sub.uri"), EntityType.CLASS))
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
                        .map(r -> asOWLEntity(r.get("sup.uri"), EntityType.CLASS))
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
                        .map(r -> asOWLEntity(r.get("sub.uri"), EntityType.OBJECT_PROPERTY))
                        .collect(Collectors.toCollection(TreeSet::new));
            });
        }
    }

    @Override
    protected SortedSet<OWLObjectProperty> getSuperPropertiesImpl(OWLObjectProperty p) {
        try (Session session = driver.session()) {
            return session.readTransaction((TransactionWork<SortedSet<OWLObjectProperty>>) tx -> {
                StatementResult result = tx.run(SUPERPROPERTIES_OP_TEMPLATE,
                        parameters("uri", p.toStringID()));
                return result.stream()
                        .map(r -> asOWLEntity(r.get("sup.uri"), EntityType.OBJECT_PROPERTY))
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
                        .map(r -> asOWLEntity(r.get("sub.uri"), EntityType.DATA_PROPERTY))
                        .collect(Collectors.toCollection(TreeSet::new));
            });
        }
    }

    @Override
    protected SortedSet<OWLDataProperty> getSuperPropertiesImpl(OWLDataProperty p) {
        try (Session session = driver.session()) {
            return session.readTransaction((TransactionWork<SortedSet<OWLDataProperty>>) tx -> {
                StatementResult result = tx.run(SUPERPROPERTIES_DP_TEMPLATE,
                        parameters("uri", p.toStringID()));
                return result.stream()
                        .map(r -> asOWLEntity(r.get("sup.uri"), EntityType.DATA_PROPERTY))
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
                        .map(r -> asOWLEntity(r.get("dom.uri"), EntityType.CLASS))
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
                        .map(r -> asOWLEntity(r.get("dom.uri"), EntityType.CLASS))
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
                        .map(r -> asOWLEntity(r.get("ran.uri"), EntityType.CLASS))
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
                   return asOWLEntity(result.next().get("ran.uri"), EntityType.DATATYPE);
               }

               return null;
            });
        }
    }

    /*************************************
     * ABox queries                      *
     *************************************/

    private OWLIndividual valToInd(Value val) {
        return df.getOWLNamedIndividual(IRI.create(val.asString()));
    }

    @Override
    protected SortedSet<OWLIndividual> getIndividualsImpl(OWLClassExpression ce) {
        if(ce.isOWLNothing()) {
            return Collections.emptySortedSet();
        } else {
            String query;

            if(ce.isOWLThing()) {
               query = ALL_INDIVIDUALS;
            } else {
                OWLClassExpressionToCypherConverter conv = new OWLClassExpressionToCypherConverter();
                conv.setUseInference(useInference);
                query = conv.convert(ce, "n");
            }
            System.out.println(query);
            try (Session session = driver.session()) {
                return session.readTransaction((TransactionWork<SortedSet<OWLIndividual>>) tx -> {
                    StatementResult result = tx.run(query);
                    return result.stream()
                            .map(r -> df.getOWLNamedIndividual(IRI.create(r.get("n").asNode().get("uri").asString())))
                            .collect(Collectors.toCollection(TreeSet::new));
                });
            }
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

    final String PROPERTY_MEMBERS = "match path=(s:Resource)-[p]->(o) WHERE s.uri = $uri\n" +
            "RETURN path";


    @Override
    protected Map<OWLObjectProperty, Set<OWLIndividual>> getObjectPropertyRelationshipsImpl(OWLIndividual individual) {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(PROPERTY_MEMBERS,
                                                parameters("uri", individual.toStringID()));

                return result.stream()
                        .map(r -> r.get("path").asPath())
                        .collect(groupingBy(p -> df.getOWLObjectProperty(IRI.create(p.relationships().iterator().next().type())),
                                            mapping(p -> asOWLEntity(p.end().get("uri"), EntityType.NAMED_INDIVIDUAL),
                                                    toSet())));

            });
        }
    }

    final String DATA_PROPERTY_MEMBERS = "match(n:Resource) WHERE n.uri=$uri RETURN n";

    final Set<String> PROPERTY_BLACKLIST = Sets.newHashSet("uri", RDFS.label.getURI());
    @Override
    protected Map<OWLDataProperty, Set<OWLLiteral>> getDataPropertyRelationshipsImpl(OWLIndividual individual) {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(DATA_PROPERTY_MEMBERS,
                        parameters("uri", individual.toStringID()));

                if (result.hasNext()) {
                    Node node = result.single().get("n").asNode();

                    Map<OWLDataProperty, Set<OWLLiteral>> res = new HashMap<>();
                    Streams.stream(node.keys())
                            .filter(k -> !PROPERTY_BLACKLIST.contains(k))
                            .forEach(k -> {
                                OWLDataProperty p = df.getOWLDataProperty(IRI.create(k));
                                Set<OWLLiteral> lit = asOWLLiterals(node.get(k));
                                res.put(p, lit);
                            });

                    return res;
                } else {
                    throw new RuntimeException(String.format("individual % not found in KB", individual.toString()));
                }

            });
        }
    }

    private Set<OWLLiteral> asOWLLiterals(Value value) {
        List<Object> objects = value.asObject() instanceof List
                                    ? value.asList()
                                    : Collections.singletonList(value.asObject());

        return objects.stream().map(o -> {
            OWLLiteral lit;
            if (o instanceof Integer) {
                lit = df.getOWLLiteral((Integer) o);
            } else if (o instanceof Float) {
                lit = df.getOWLLiteral((Float) o);
            } else if (o instanceof String) {
                lit = df.getOWLLiteral((String) o);
            } else if (o instanceof Boolean) {
                lit = df.getOWLLiteral((Boolean) o);
            } else {
                return Optional.<OWLLiteral>empty();
            }
            return Optional.of(lit);
        }).flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
                .collect(toSet());
    }

    public static void main(String[] args) throws Exception {
        OWLDataFactory df = OWLManager.getOWLDataFactory();

        try(Neo4JKS ks = new Neo4JKS("bolt://localhost:7687", "neo4j", "123pw")) {

            ks.init();

            CypherReasoner reasoner = new CypherReasoner(ks);
            reasoner.setUseInference(true);
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

            ce = df.getOWLObjectIntersectionOf(
                    df.getOWLClass(IRI.create("http://ns.softwiki.de/req/CustomerRequirement")),
                    df.getOWLClass(IRI.create("http://ns.softwiki.de/req/Requirement"))
            );
            individuals = reasoner.getIndividuals(ce);
            System.out.println(individuals);

            Map<OWLObjectProperty, Set<OWLIndividual>> objectPropertyRelationships =
                    reasoner.getObjectPropertyRelationships(df.getOWLNamedIndividual(IRI.create("http://ns.softwiki.de/req/LogEveryUserActivity")));

            objectPropertyRelationships.forEach((k, v) -> System.out.println(k + ":" + v));

            Map<OWLDataProperty, Set<OWLLiteral>> dataPropertyRelationships =
                    reasoner.getDataPropertyRelationshipsImpl(df.getOWLNamedIndividual(IRI.create("http://ns.softwiki.de/req/LogEveryUserActivity")));

            dataPropertyRelationships.forEach((k, v) -> System.out.println(k + ":" + v));


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

//            alg.start();

        }

    }
}
