package org.dllearner.reasoning;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import org.apache.jena.vocabulary.RDFS;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentAnn;
import org.dllearner.core.ComponentInitException;
import org.dllearner.kb.Neo4JKS;
import org.dllearner.learningproblems.ClassLearningProblem;
import org.dllearner.refinementoperators.RhoDRDown;
import org.dllearner.utilities.neo4j.NeoSemanticsPluginSettings;
import org.dllearner.utilities.neo4j.OWLClassExpressionToCypherConverter;
import org.neo4j.driver.v1.*;
import org.neo4j.driver.v1.types.Node;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.util.stream.Collectors.*;
import static org.dllearner.reasoning.CypherReasoner.QueryTemplate.*;
import static org.neo4j.driver.v1.Values.parameters;

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

    private static final Logger log = LoggerFactory.getLogger(CypherReasoner.class);

    private final Neo4JKS ks;
    private Driver driver;

    private NeoSemanticsPluginSettings ctx = NeoSemanticsPluginSettings.STANDARD;

    private boolean useInference = false;

    public CypherReasoner(Neo4JKS ks) {
        this.ks = ks;

        driver = ks.getDriver();
    }

    public void setNeoSemanticsPluginSettings(NeoSemanticsPluginSettings settings) {
        this.ctx = settings;
    }

    @Override
    public ReasonerType getReasonerType() {
        return ReasonerType.CYPHER;
    }

    @Override
    public void releaseKB() {
    }

    public void setUseInference(boolean useInference) {
        this.useInference = useInference;
    }

    @Override
    public OWLDatatype getDatatype(OWLDataProperty dp) {
        throw new UnsupportedOperationException("can't get datatype of a data property because" +
                "in Neo4J datatypes are just implicitely attached to the property values of a resource node.");
    }

    @Override
    public void setSynchronized() {
        throw new UnsupportedOperationException("synchronized operations not supported yet!");
    }

    @Override
    public SortedSet<OWLIndividual> getIndividuals() {
        return getIndividuals(df.getOWLThing());
    }

    @Override
    public String getBaseURI() {
//        throw new UnsupportedOperationException("getting base URI not supported yet!");
        log.warn("getting base URI not supported in Cypher reasoner, returning 'null'");
        return null;
    }

    @Override
    public Map<String, String> getPrefixes() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run("CALL semantics.listNamespacePrefixes()");

                return result.stream().collect(
                        toMap(r -> r.get("prefix").asString(),
                                r -> r.get("namespace").asString()));
            });
        }
    }

    @Override
    public void init() throws ComponentInitException {
        updateQueryTemplates();
    }

    private void updateQueryTemplates() {
        queries.replaceAll((key, val) ->
                val
                        .replace("RESOURCE_LABEL", ctx.getResourceLabel())
                        .replace("CLASS_LABEL", ctx.getClassLabel())
                        .replace("OBJECT_PROPERTY_LABEL", ctx.getObjectPropertyLabel())
                        .replace("DATA_PROPERTY_LABEL", ctx.getDataPropertyLabel())
                        .replace("SUBCLASSOF_REL", ctx.getSubClassOfRel())
                        .replace("SUBPROPERTYOF_REL", ctx.getSubPropertyOfRel())
                        .replace("DOMAIN_REL", ctx.getDomainRel())
                        .replace("RANGE_REL", ctx.getRangeRel())
        );
    }

    private final QueryRegistry queries = new QueryRegistry();

    class QueryRegistry extends HashMap<QueryTemplate, String>{
        private String CLASSES_TEMPLATE = "MATCH (cls:`CLASS_LABEL`) RETURN cls.uri";
        private String OP_TEMPLATE = "MATCH (p:`OBJECT_PROPERTY_LABEL`) RETURN p.uri";
        private String DP_TEMPLATE = "MATCH (p:`DATA_PROPERTY_LABEL`) RETURN p.uri";
        private String DP_BOOLEAN_TEMPLATE = "MATCH (p:`DATA_PROPERTY_LABEL`)-[:`RANGE_REL`]->(ran:`RESOURCE_LABEL` {uri: 'http://www.w3.org/2001/XMLSchema#boolean'}) " +
                "RETURN p.uri";
        private String DP_STRING_TEMPLATE = "MATCH (p:`DATA_PROPERTY_LABEL`)-[:`RANGE_REL`]->(ran:`RESOURCE_LABEL` {uri: 'http://www.w3.org/2001/XMLSchema#string'}) " +
                "RETURN p.uri";
        private String DP_INT_TEMPLATE = "MATCH (p:`DATA_PROPERTY_LABEL`)-[:`RANGE_REL`]->(ran:`RESOURCE_LABEL` ) " +
                "WHERE ran.uri IN ['http://www.w3.org/2001/XMLSchema#int', 'http://www.w3.org/2001/XMLSchema#integer'] " +
                "RETURN p.uri";
        private String DP_DOUBLE_TEMPLATE = "MATCH (p:`DATA_PROPERTY_LABEL`)-[:`RANGE_REL`]->(ran:`RESOURCE_LABEL` ) " +
                "WHERE ran.uri IN ['http://www.w3.org/2001/XMLSchema#double', 'http://www.w3.org/2001/XMLSchema#float'] " +
                "RETURN p.uri";



        //    final String SCO_TEMPLATE = "MATCH (sub:`CLASS_LABEL`)-[:`SUBCLASSOF_REL`]->(sup:`CLASS_LABEL` {uri: $uri}) return sub.uri";
        private String SCO_TEMPLATE = "MATCH (sub:`CLASS_LABEL`)-[:`SUBCLASSOF_REL`]->(sup {uri: $uri}) RETURN sub.uri";
        // a bit tricky because depending on the data loaded an edge (cls -rdfs:subClassOf-> owl:Thing) might exist or not
//    final String ROOT_CLASSES_TEMPLATE = "MATCH (cls:`CLASS_LABEL`)-[:`SUBCLASSOF_REL`]->(sup {uri: $uri})\n" +
//            "RETURN cls.uri\n" +
//            "UNION \n" +
//            "MATCH (cls:`CLASS_LABEL`) \n" +
//            " WHERE NOT (cls)-[:`SUBCLASSOF_REL`]->()\n" +
//            " RETURN cls.uri";
        private String ROOT_CLASSES_TEMPLATE = "MATCH (sub:`CLASS_LABEL`)-[:`SUBCLASSOF_REL`]->(sup {uri: $uri})\n" +
                "RETURN sub.uri\n" +
                "UNION \n" +
                "MATCH (sub:`CLASS_LABEL`) \n" +
                " WHERE NOT (sub)-[:`SUBCLASSOF_REL`]->()\n" +
                " RETURN sub.uri";

        private String SUPERCLASSES_TEMPLATE = "MATCH (sub:`CLASS_LABEL` {uri: $uri})-[:`SUBCLASSOF_REL`]->(sup:`CLASS_LABEL`) RETURN sup.uri";
        private String IS_SCO_TEMPLATE = "MATCH (sub:`CLASS_LABEL` {uri: $sub_uri})-[:`SUBCLASSOF_REL`]->(sup:`CLASS_LABEL` {uri: $sup_uri}) RETURN sub.uri";
        private String SPO_OP_TEMPLATE = "MATCH (sub:`OBJECT_PROPERTY_LABEL`)-[:`SUBPROPERTYOF_REL`]->(sup:`OBJECT_PROPERTY_LABEL` {uri: $uri}) RETURN sub.uri";
        private String SPO_DP_TEMPLATE = "MATCH (sub:`DATA_PROPERTY_LABEL`)-[:`SUBPROPERTYOF_REL`]->(sup:`DATA_PROPERTY_LABEL` {uri: $uri}) RETURN sub.uri";
        private String SUPERPROPERTIES_OP_TEMPLATE = "MATCH (sub:`OBJECT_PROPERTY_LABEL` {uri: $uri})-[:`SUBPROPERTYOF_REL`]->(sup:`OBJECT_PROPERTY_LABEL`) RETURN sup.uri";
        private String SUPERPROPERTIES_DP_TEMPLATE = "MATCH (sub:`DATA_PROPERTY_LABEL` {uri: $uri})-[:`SUBPROPERTYOF_REL`]->(sup:`DATA_PROPERTY_LABEL`) RETURN sup.uri";
        private String DOMAIN_OP_TEMPLATE = "MATCH (p:`OBJECT_PROPERTY_LABEL` {uri: $uri})-[:`DOMAIN_REL`]->(dom:`CLASS_LABEL` ) RETURN dom.uri";
        private String DOMAIN_DP_TEMPLATE = "MATCH (p:`DATA_PROPERTY_LABEL` {uri: $uri})-[:`DOMAIN_REL`]->(dom:`CLASS_LABEL`) RETURN dom.uri";
        private String RANGE_OP_TEMPLATE = "MATCH (p:`OBJECT_PROPERTY_LABEL` {uri: $uri})-[:`RANGE_REL`]->(ran:`CLASS_LABEL`) RETURN ran.uri";
        private String RANGE_DP_TEMPLATE = "MATCH (p:`DATA_PROPERTY_LABEL` {uri: $uri})-[:`RANGE_REL`]->(ran:`RESOURCE_LABEL`) RETURN ran.uri";

        private String OP_MEMBERS = "MATCH (s:`RESOURCE_LABEL`)-[r: `%s`]->(o:`RESOURCE_LABEL`) RETURN s.uri, o.uri";
        private String DP_MEMBERS = "MATCH (s:`RESOURCE_LABEL`) WHERE EXISTS (s.{uri: $uri}) RETURN s";

        private String ALL_INDIVIDUALS = "with ['CLASS_LABEL', 'OBJECT_PROPERTY_LABEL', 'DATA_PROPERTY_LABEL'] as blacklist\n" +
                "match (n:`RESOURCE_LABEL`) \n" +
                "where not any (l in labels(n) where l in blacklist)  \n" +
                "return n";
        private String OBJECT_PROPERTY_MEMBERS = "match path=(s:`RESOURCE_LABEL`)-[p]->(o) WHERE s.uri = $uri\n" +
                "RETURN path";
        private String DATA_PROPERTY_MEMBERS = "match(n:`RESOURCE_LABEL`) WHERE n.uri=$uri RETURN n";

        public QueryRegistry() {
            super();
            put(QueryTemplate.CLASSES_TEMPLATE, CLASSES_TEMPLATE);
            put(QueryTemplate.OP_TEMPLATE, OP_TEMPLATE);
            put(QueryTemplate.DP_TEMPLATE, DP_TEMPLATE);
            put(QueryTemplate.DP_BOOLEAN_TEMPLATE, DP_BOOLEAN_TEMPLATE);
            put(QueryTemplate.DP_STRING_TEMPLATE, DP_STRING_TEMPLATE);
            put(QueryTemplate.DP_INT_TEMPLATE, DP_INT_TEMPLATE);
            put(QueryTemplate.DP_DOUBLE_TEMPLATE, DP_DOUBLE_TEMPLATE);
            put(QueryTemplate.SCO_TEMPLATE, SCO_TEMPLATE);
            put(QueryTemplate.ROOT_CLASSES_TEMPLATE, ROOT_CLASSES_TEMPLATE);
            put(QueryTemplate.SUPERCLASSES_TEMPLATE, SUPERCLASSES_TEMPLATE);
            put(QueryTemplate.IS_SCO_TEMPLATE, IS_SCO_TEMPLATE);
            put(QueryTemplate.SPO_OP_TEMPLATE, SPO_OP_TEMPLATE);
            put(QueryTemplate.SPO_DP_TEMPLATE, SPO_DP_TEMPLATE);
            put(QueryTemplate.SUPERPROPERTIES_OP_TEMPLATE, SUPERPROPERTIES_OP_TEMPLATE);
            put(QueryTemplate.SUPERPROPERTIES_DP_TEMPLATE, SUPERPROPERTIES_DP_TEMPLATE);
            put(QueryTemplate.DOMAIN_OP_TEMPLATE, DOMAIN_OP_TEMPLATE);
            put(QueryTemplate.DOMAIN_DP_TEMPLATE, DOMAIN_DP_TEMPLATE);
            put(QueryTemplate.RANGE_OP_TEMPLATE, RANGE_OP_TEMPLATE);
            put(QueryTemplate.RANGE_DP_TEMPLATE, RANGE_DP_TEMPLATE);
            put(QueryTemplate.OP_MEMBERS, OP_MEMBERS);
            put(QueryTemplate.DP_MEMBERS, DP_MEMBERS);
            put(QueryTemplate.ALL_INDIVIDUALS, ALL_INDIVIDUALS);
            put(QueryTemplate.OBJECT_PROPERTY_MEMBERS, OBJECT_PROPERTY_MEMBERS);
            put(QueryTemplate.DATA_PROPERTY_MEMBERS, DATA_PROPERTY_MEMBERS);
        }
    }

    enum QueryTemplate {
        CLASSES_TEMPLATE,
        OP_TEMPLATE, DP_TEMPLATE,
        DP_BOOLEAN_TEMPLATE, DP_STRING_TEMPLATE, DP_INT_TEMPLATE, DP_DOUBLE_TEMPLATE,
        SCO_TEMPLATE, ROOT_CLASSES_TEMPLATE, SUPERCLASSES_TEMPLATE, IS_SCO_TEMPLATE,
        SPO_OP_TEMPLATE, SPO_DP_TEMPLATE,
        SUPERPROPERTIES_OP_TEMPLATE, SUPERPROPERTIES_DP_TEMPLATE,
        DOMAIN_OP_TEMPLATE, DOMAIN_DP_TEMPLATE,
        RANGE_OP_TEMPLATE, RANGE_DP_TEMPLATE,
        OP_MEMBERS, DP_MEMBERS,
        ALL_INDIVIDUALS,
        OBJECT_PROPERTY_MEMBERS, DATA_PROPERTY_MEMBERS
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
    public Set<OWLClass> getClasses() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(queries.get(CLASSES_TEMPLATE));
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
                StatementResult result = tx.run(queries.get(OP_TEMPLATE));
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
                StatementResult result = tx.run(queries.get(DP_TEMPLATE));
                return result.stream()
                        .map(r -> asOWLEntity(r.get("p.uri"), EntityType.DATA_PROPERTY))
                        .collect(Collectors.toSet());
            });
        }
    }

    @Override
    protected Set<OWLDataProperty> getBooleanDatatypePropertiesImpl() {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(queries.get(DP_BOOLEAN_TEMPLATE));
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
                StatementResult result = tx.run(queries.get(DP_STRING_TEMPLATE));
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
                StatementResult result = tx.run(queries.get(DP_INT_TEMPLATE));
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
                StatementResult result = tx.run(queries.get(DP_DOUBLE_TEMPLATE));
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
                        ? tx.run(queries.get(ROOT_CLASSES_TEMPLATE),
                                    parameters("uri", ce.asOWLClass().toStringID()))
                        : tx.run(queries.get(SCO_TEMPLATE),
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
                StatementResult result = tx.run(queries.get(SUPERCLASSES_TEMPLATE),
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
                StatementResult result = tx.run(queries.get(IS_SCO_TEMPLATE),
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
                StatementResult result = tx.run(queries.get(SPO_OP_TEMPLATE),
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
                StatementResult result = tx.run(queries.get(SUPERPROPERTIES_OP_TEMPLATE),
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
                StatementResult result = tx.run(queries.get(SPO_DP_TEMPLATE),
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
                StatementResult result = tx.run(queries.get(SUPERPROPERTIES_DP_TEMPLATE),
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
                StatementResult result = tx.run(queries.get(DOMAIN_OP_TEMPLATE),
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
                StatementResult result = tx.run(queries.get(DOMAIN_DP_TEMPLATE),
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
                StatementResult result = tx.run(queries.get(RANGE_OP_TEMPLATE),
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
                StatementResult result = tx.run(queries.get(RANGE_DP_TEMPLATE),
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
        return asOWLEntity(val, EntityType.NAMED_INDIVIDUAL);
    }

    @Override
    protected SortedSet<OWLIndividual> getIndividualsImpl(OWLClassExpression ce) {
        if(ce.isOWLNothing()) {
            return Collections.emptySortedSet();
        } else {
            String query;

            if(ce.isOWLThing()) {
               query = queries.get(ALL_INDIVIDUALS);
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
                            .map(r -> asOWLEntity(r.get("n").asNode().get("uri"), EntityType.NAMED_INDIVIDUAL))
                            .collect(Collectors.toCollection(TreeSet::new));
                });
            }
        }
    }

    @Override
    protected Map<OWLIndividual, SortedSet<OWLIndividual>> getPropertyMembersImpl(OWLObjectProperty p) {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(String.format(queries.get(OP_MEMBERS), p.toStringID()));

                return result.stream()
                        .collect(Collectors.groupingBy(r -> asOWLEntity(r.get("s.uri"), EntityType.NAMED_INDIVIDUAL),
                                    Collectors.mapping(r -> asOWLEntity(r.get("o.uri"), EntityType.NAMED_INDIVIDUAL),
                                    Collectors.toCollection(TreeSet::new))));
                });
            }
    }

    @Override
    protected Map<OWLIndividual, SortedSet<OWLLiteral>> getDatatypeMembersImpl(OWLDataProperty p) {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(queries.get(DP_MEMBERS), parameters("uri", p.toStringID()));

                Map<OWLIndividual, SortedSet<OWLLiteral>> map = result.stream()
                        .map(r -> r.get("s").asNode())
                        .collect(toMap(n -> asOWLEntity(n.get("uri"), EntityType.NAMED_INDIVIDUAL),
                                n -> asOWLLiterals(n.get(p.toStringID()))));


                return map;
                });
        }
    }

    @Override
    protected Map<OWLObjectProperty, Set<OWLIndividual>> getObjectPropertyRelationshipsImpl(OWLIndividual individual) {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(queries.get(OBJECT_PROPERTY_MEMBERS),
                                                parameters("uri", individual.toStringID()));

                return result.stream()
                        .map(r -> r.get("path").asPath())
                        .collect(groupingBy(p -> df.getOWLObjectProperty(IRI.create(p.relationships().iterator().next().type())),
                                            mapping(p -> asOWLEntity(p.end().get("uri"), EntityType.NAMED_INDIVIDUAL),
                                                    toSet())));

            });
        }
    }



    final Set<String> PROPERTY_BLACKLIST = Sets.newHashSet("uri", RDFS.label.getURI());
    @Override
    protected Map<OWLDataProperty, Set<OWLLiteral>> getDataPropertyRelationshipsImpl(OWLIndividual individual) {
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> {
                StatementResult result = tx.run(queries.get(DATA_PROPERTY_MEMBERS),
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
                    throw new RuntimeException(String.format("individual %s not found in KB", individual.toString()));
                }

            });
        }
    }

    private SortedSet<OWLLiteral> asOWLLiterals(Value value) {
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
                .collect(Collectors.toCollection(TreeSet::new));
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
